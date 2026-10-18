package ru.gusev.tastyandtochka

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderApiTest {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17")

        private const val BURGER_ID = "20000000-0000-0000-0000-000000000001"
        private const val FRIES_ID = "20000000-0000-0000-0000-000000000003"
        private const val UNAVAILABLE_ID = "20000000-0000-0000-0000-000000000006"
        private const val MISSING_ID = "00000000-0000-0000-0000-000000000000"
    }

    private lateinit var context: ConfigurableApplicationContext
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper()

    private val jdbc: JdbcTemplate
        get() = JdbcTemplate(context.getBean(DataSource::class.java))

    @BeforeAll
    fun start() {
        context = startApplication()
    }

    @AfterAll
    fun stop() {
        if (::context.isInitialized) context.close()
    }

    @Test
    fun `creates an order with server prices and returns it by Location`() {
        val response = post(defaultRequest())
        val order = body(response, 201)
        val location = response.headers().firstValue("Location").orElseThrow()

        assertEquals("/api/v1/orders/${order["id"].asString()}", location)
        assertEquals("CREATED", order["status"].asString())
        assertEquals(0L, order["version"].asLong())
        assertEquals(31700L, order["totalPriceInKopecks"].asLong())
        assertTrue(Instant.parse(order["createdAt"].asString()) <= Instant.now())
        assertEquals(2, order["items"].size())
        val burger = order["items"].single { it["productId"].asString() == BURGER_ID }
        assertEquals("Чизбургер", burger["productName"].asString())
        assertEquals(9900L, burger["unitPriceInKopecks"].asLong())
        assertEquals(2L, burger["quantity"].asLong())
        assertEquals(19800L, burger["totalPriceInKopecks"].asLong())
        assertEquals(order, body(get(location), 200))
    }

    @Test
    fun `order survives an application restart`() {
        val order = body(post(defaultRequest()), 201)

        context.close()
        context = startApplication()

        assertEquals(order, body(get("/api/v1/orders/${order["id"].asString()}"), 200))
    }

    @Test
    fun `keeps purchased prices and names after the catalog changes`() {
        val order = body(post(defaultRequest()), 201)
        val productId = UUID.fromString(BURGER_ID)
        val original = jdbc.queryForMap("SELECT name, price_in_kopecks, available FROM products WHERE id = ?", productId)
        try {
            jdbc.update(
                "UPDATE products SET name = ?, price_in_kopecks = ?, available = FALSE WHERE id = ?",
                "Changed burger", 99900, productId,
            )

            assertEquals(order, body(get("/api/v1/orders/${order["id"].asString()}"), 200))
        } finally {
            jdbc.update(
                "UPDATE products SET name = ?, price_in_kopecks = ?, available = ? WHERE id = ?",
                original["name"], original["price_in_kopecks"], original["available"], productId,
            )
        }
    }

    @Test
    fun `reads persisted status and version`() {
        val order = body(post(defaultRequest()), 201)
        val id = UUID.fromString(order["id"].asString())
        jdbc.update("UPDATE orders SET status = 'READY', version = 7 WHERE id = ?", id)

        val restored = body(get("/api/v1/orders/$id"), 200)

        assertEquals("READY", restored["status"].asString())
        assertEquals(7L, restored["version"].asLong())
        assertEquals(order["items"], restored["items"])
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{}",
        "{",
        """{"items":null}""",
        """{"items":[]}""",
        """{"items":[null]}""",
        """{"items":[{"quantity":1}]}""",
        """{"items":[{"productId":null,"quantity":1}]}""",
        """{"items":[{"productId":"not-a-uuid","quantity":1}]}""",
        """{"items":[{"productId":"20000000-0000-0000-0000-000000000001","quantity":0}]}""",
        """{"items":[{"productId":"20000000-0000-0000-0000-000000000001","quantity":100}]}""",
        """{"items":[{"productId":"20000000-0000-0000-0000-000000000001"}]}""",
    ])
    fun `rejects invalid requests without creating an order`(request: String) {
        val ordersBefore = count("orders")
        val itemsBefore = count("order_items")

        body(post(request), 400)

        assertEquals(ordersBefore, count("orders"))
        assertEquals(itemsBefore, count("order_items"))
    }

    @Test
    fun `rejects duplicate products`() {
        val ordersBefore = count("orders")
        val request = """{"items":[{"productId":"$BURGER_ID","quantity":1},{"productId":"$BURGER_ID","quantity":2}]}"""

        assertEquals("INVALID_ORDER", body(post(request), 400)["code"].asString())
        assertEquals(ordersBefore, count("orders"))
    }

    @Test
    fun `rejects more than one hundred order items`() {
        val items = (1..101).joinToString(",") { """{"productId":"${UUID.randomUUID()}","quantity":1}""" }

        body(post("""{"items":[$items]}"""), 400)
    }

    @Test
    fun `rejects a missing product instead of creating a partial order`() {
        val ordersBefore = count("orders")
        val request = """{"items":[{"productId":"$BURGER_ID","quantity":1},{"productId":"$MISSING_ID","quantity":1}]}"""

        val error = body(post(request), 422)

        assertEquals("PRODUCTS_NOT_FOUND", error["code"].asString())
        assertEquals(MISSING_ID, error["productIds"].single().asString())
        assertEquals(ordersBefore, count("orders"))
    }

    @Test
    fun `rejects an unavailable product`() {
        val ordersBefore = count("orders")
        val request = """{"items":[{"productId":"$UNAVAILABLE_ID","quantity":1}]}"""

        val error = body(post(request), 422)

        assertEquals("PRODUCTS_UNAVAILABLE", error["code"].asString())
        assertEquals(UNAVAILABLE_ID, error["productIds"].single().asString())
        assertEquals(ordersBefore, count("orders"))
    }

    @Test
    fun `rejects an overflowing amount without creating an order`() {
        val productId = UUID.fromString(BURGER_ID)
        val originalPrice = jdbc.queryForObject("SELECT price_in_kopecks FROM products WHERE id = ?", Long::class.java, productId)!!
        val ordersBefore = count("orders")
        try {
            jdbc.update("UPDATE products SET price_in_kopecks = ? WHERE id = ?", Long.MAX_VALUE, productId)

            assertEquals("ORDER_AMOUNT_TOO_LARGE", body(post(defaultRequest()), 422)["code"].asString())
            assertEquals(ordersBefore, count("orders"))
        } finally {
            jdbc.update("UPDATE products SET price_in_kopecks = ? WHERE id = ?", originalPrice, productId)
        }
    }

    @Test
    fun `rolls back the order and first item when saving the second item fails`() {
        val ordersBefore = count("orders")
        val itemsBefore = count("order_items")
        // Only this test database is modified. PostgreSQL itself rejects the second insert.
        jdbc.execute(
            "ALTER TABLE order_items ADD CONSTRAINT test_reject_fries CHECK (product_id <> '$FRIES_ID'::uuid) NOT VALID",
        )
        try {
            val response = post(defaultRequest())

            assertEquals("INTERNAL_ERROR", body(response, 500)["code"].asString())
            assertFalse(response.body().contains("test_reject_fries"))
            assertEquals(ordersBefore, count("orders"))
            assertEquals(itemsBefore, count("order_items"))
        } finally {
            jdbc.execute("ALTER TABLE order_items DROP CONSTRAINT test_reject_fries")
        }
    }

    @Test
    fun `returns not found for an unknown order`() {
        assertEquals("ORDER_NOT_FOUND", body(get("/api/v1/orders/$MISSING_ID"), 404)["code"].asString())
    }

    @Test
    fun `rejects an invalid order identifier`() {
        assertEquals("INVALID_REQUEST", body(get("/api/v1/orders/not-a-uuid"), 400)["code"].asString())
    }

    private fun startApplication(): ConfigurableApplicationContext =
        SpringApplicationBuilder(TastyAndTochkaApplication::class.java).run(
            "--spring.datasource.url=${postgres.jdbcUrl}",
            "--spring.datasource.username=${postgres.username}",
            "--spring.datasource.password=${postgres.password}",
            "--server.port=0",
        )

    private fun defaultRequest() =
        """{"items":[{"productId":"$FRIES_ID","quantity":1},{"productId":"$BURGER_ID","quantity":2}]}"""

    private fun post(body: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(uri("/api/v1/orders"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
    )

    private fun get(path: String): HttpResponse<String> = send(HttpRequest.newBuilder(uri(path)).GET().build())

    private fun uri(path: String): URI =
        URI.create("http://localhost:${context.environment.getRequiredProperty("local.server.port")}$path")

    private fun send(request: HttpRequest): HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun body(response: HttpResponse<String>, expectedStatus: Int): JsonNode {
        assertEquals(expectedStatus, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun count(table: String): Long = jdbc.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!
}
