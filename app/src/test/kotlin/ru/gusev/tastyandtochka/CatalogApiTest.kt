package ru.gusev.tastyandtochka

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.gusev.tastyandtochka.catalog.api.CatalogQuery
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogApiTest {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:${postgres.jdbcUrl.removePrefix("jdbc:")}" }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var environment: Environment

    private val jdbc: JdbcTemplate
        get() = JdbcTemplate(testDataSource(postgres))

    @Autowired
    private lateinit var catalog: CatalogQuery

    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper()

    @Test
    fun `returns categories from PostgreSQL`() {
        val categories = getJson("/api/v1/categories")
        val expected = jdbc.query("SELECT id, name FROM categories") { row, _ ->
            row.getString("id") to row.getString("name")
        }.toMap()

        assertTrue(categories.isArray)
        assertEquals(3, categories.size())
        assertEquals(expected, categories.associate { it["id"].asString() to it["name"].asString() })
    }

    @Test
    fun `returns menu with prices in kopecks and availability`() {
        val products = getJson("/api/v1/products")
        val burger = products.single { it["id"].asString() == "20000000-0000-0000-0000-000000000002" }
        val unavailable = products.single { it["id"].asString() == "20000000-0000-0000-0000-000000000006" }

        assertEquals(6, products.size())
        assertEquals("10000000-0000-0000-0000-000000000001", burger["categoryId"].asString())
        assertEquals("Двойной чизбургер", burger["name"].asString())
        assertEquals("Две говяжьи котлеты, сыр и соус", burger["description"].asString())
        assertEquals(19900L, burger["priceInKopecks"].asLong())
        assertTrue(burger["available"].asBoolean())
        assertFalse(unavailable["available"].asBoolean())
    }

    @Test
    fun `filters products by category`() {
        val categoryId = "10000000-0000-0000-0000-000000000001"
        val products = getJson("/api/v1/products?categoryId=$categoryId")

        assertEquals(2, products.size())
        assertTrue(products.all { it["categoryId"].asString() == categoryId })
    }

    @Test
    fun `returns an empty list for an unknown category`() {
        val products = getJson("/api/v1/products?categoryId=00000000-0000-0000-0000-000000000000")

        assertTrue(products.isArray)
        assertEquals(0, products.size())
    }

    @Test
    fun `rejects an invalid category identifier`() {
        assertEquals(400, get("/api/v1/products?categoryId=not-a-uuid").statusCode())
    }

    @Test
    fun `reads changed database values instead of a static menu`() {
        val id = UUID.fromString("20000000-0000-0000-0000-000000000001")
        val originalPrice = jdbc.queryForObject("SELECT price_in_kopecks FROM products WHERE id = ?", Long::class.java, id)!!
        try {
            jdbc.update("UPDATE products SET price_in_kopecks = ? WHERE id = ?", originalPrice + 100, id)

            val product = getJson("/api/v1/products").single { it["id"].asString() == id.toString() }

            assertEquals(originalPrice + 100, product["priceInKopecks"].asLong())
        } finally {
            jdbc.update("UPDATE products SET price_in_kopecks = ? WHERE id = ?", originalPrice, id)
        }
    }

    @Test
    fun `public catalog query returns requested products including unavailable ones`() {
        val availableId = UUID.fromString("20000000-0000-0000-0000-000000000002")
        val unavailableId = UUID.fromString("20000000-0000-0000-0000-000000000006")
        val missingId = UUID.fromString("00000000-0000-0000-0000-000000000000")

        val products = catalog.findProducts(setOf(availableId, unavailableId, missingId)).block()!!

        assertEquals(setOf(availableId, unavailableId), products.map { it.id }.toSet())
        assertEquals(19900L, products.single { it.id == availableId }.priceInKopecks)
        assertFalse(products.single { it.id == unavailableId }.available)
    }

    @Test
    fun `public catalog query accepts an empty set`() {
        assertTrue(catalog.findProducts(emptySet()).block()!!.isEmpty())
    }

    private fun get(path: String): HttpResponse<String> {
        val port = environment.getRequiredProperty("local.server.port")
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun getJson(path: String): JsonNode {
        val response = get(path)
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"))
        return json.readTree(response.body())
    }
}
