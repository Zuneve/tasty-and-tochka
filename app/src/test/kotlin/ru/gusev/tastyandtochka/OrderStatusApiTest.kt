package ru.gusev.tastyandtochka

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import javax.sql.DataSource

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderStatusApiTest {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17")
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
    fun `advances the lifecycle and persists the changed status across a restart`() {
        val original = createOrder()
        val id = original["id"].asString()
        val preparing = body(changeStatus(id, "PREPARING", "CREATED"), 200)
        assertEquals("PREPARING", preparing["status"].asString())
        assertTrue(!preparing.has("version"))
        assertEquals(original["items"], preparing["items"])
        assertEquals(original["totalPriceInKopecks"], preparing["totalPriceInKopecks"])
        assertEquals(original["createdAt"], preparing["createdAt"])

        context.close()
        context = startApplication()
        assertEquals(preparing, getOrder(id))

        val ready = body(changeStatus(id, "READY", "PREPARING"), 200)
        assertEquals("READY", ready["status"].asString())
        val completed = body(changeStatus(id, "COMPLETED", "READY"), 200)
        assertEquals("COMPLETED", completed["status"].asString())
        assertEquals(completed, getOrder(id))
    }

    @Test
    fun `selects menu products creates an order and completes it through the API`() {
        val categories = getJson("/api/v1/categories")
        val categoryId = categories.first()["id"].asString()
        val menu = getJson("/api/v1/products?categoryId=$categoryId")
        val selected = menu.filter { it["available"].asBoolean() }.take(2)
        assertEquals(2, selected.size)
        assertTrue(selected.all { it["categoryId"].asString() == categoryId })
        val requestedItems = selected.mapIndexed { index, product ->
            mapOf("productId" to product["id"].asString(), "quantity" to index + 1)
        }
        val response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mapOf("items" to requestedItems))))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val created = body(response, 201)
        val id = created["id"].asString()
        assertEquals("/api/v1/orders/$id", response.headers().firstValue("Location").orElseThrow())
        assertEquals(selected.mapIndexed { index, product -> product["priceInKopecks"].asLong() * (index + 1) }.sum(), created["totalPriceInKopecks"].asLong())
        for (product in selected) {
            val item = created["items"].single { it["productId"].asString() == product["id"].asString() }
            assertEquals(product["name"], item["productName"])
            assertEquals(product["priceInKopecks"], item["unitPriceInKopecks"])
        }
        assertEquals(created, getOrder(id))

        var current = created
        for (status in listOf("PREPARING", "READY", "COMPLETED")) {
            val previousStatus = current["status"].asString()
            current = body(changeStatus(id, status, previousStatus), 200)
            assertEquals(status, current["status"].asString())
            assertEquals(created["items"], current["items"])
            assertEquals(created["totalPriceInKopecks"], current["totalPriceInKopecks"])
            assertEquals(current, getOrder(id))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["CREATED", "READY", "COMPLETED"])
    fun `rejects a forbidden transition without changing the order`(status: String) {
        val original = createOrder()
        val id = original["id"].asString()

        val error = body(changeStatus(id, status, "CREATED"), 409)

        assertEquals("INVALID_ORDER_STATUS_TRANSITION", error["code"].asString())
        assertEquals(original, getOrder(id))
    }

    @Test
    fun `cancels a new order and rejects any further change`() {
        val id = createOrder()["id"].asString()
        val cancelled = body(changeStatus(id, "CANCELLED", "CREATED"), 200)
        assertEquals("CANCELLED", cancelled["status"].asString())

        assertEquals("INVALID_ORDER_STATUS_TRANSITION", body(changeStatus(id, "PREPARING", "CANCELLED"), 409)["code"].asString())
        assertEquals(cancelled, getOrder(id))
    }

    @Test
    fun `cannot cancel preparation or change a completed order`() {
        val id = createOrder()["id"].asString()
        val preparing = body(changeStatus(id, "PREPARING", "CREATED"), 200)

        assertEquals("INVALID_ORDER_STATUS_TRANSITION", body(changeStatus(id, "CANCELLED", "PREPARING"), 409)["code"].asString())
        assertEquals(preparing, getOrder(id))

        body(changeStatus(id, "READY", "PREPARING"), 200)
        val completed = body(changeStatus(id, "COMPLETED", "READY"), 200)
        assertEquals("INVALID_ORDER_STATUS_TRANSITION", body(changeStatus(id, "CANCELLED", "COMPLETED"), 409)["code"].asString())
        assertEquals(completed, getOrder(id))
    }

    @Test
    fun `rejects a stale status and accepts the reloaded status`() {
        val id = createOrder()["id"].asString()
        val preparing = body(changeStatus(id, "PREPARING", "CREATED"), 200)

        assertEquals("ORDER_STATUS_CONFLICT", body(changeStatus(id, "READY", "CREATED"), 409)["code"].asString())
        assertEquals(preparing, getOrder(id))

        val reloadedStatus = getOrder(id)["status"].asString()
        val ready = body(changeStatus(id, "READY", reloadedStatus), 200)
        assertEquals("READY", ready["status"].asString())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{}",
        "{",
        """{"status":null,"expectedStatus":"CREATED"}""",
        """{"status":"UNKNOWN","expectedStatus":"CREATED"}""",
        """{"expectedStatus":"CREATED"}""",
        """{"status":"PREPARING"}""",
        """{"status":"PREPARING","expectedStatus":null}""",
        """{"status":"PREPARING","expectedStatus":"UNKNOWN"}""",
        """{"status":"PREPARING","expectedStatus":{}}""",
        """{"status":"PREPARING","version":0}""",
    ])
    fun `rejects invalid status requests without changing the order`(request: String) {
        val original = createOrder()
        val id = original["id"].asString()

        body(patch(id, request), 400)

        assertEquals(original, getOrder(id))
    }

    @Test
    fun `returns not found for a missing order and bad request for an invalid identifier`() {
        assertEquals("ORDER_NOT_FOUND", body(changeStatus(UUID.randomUUID().toString(), "PREPARING", "CREATED"), 404)["code"].asString())
        assertEquals("INVALID_REQUEST", body(changeStatus("not-a-uuid", "PREPARING", "CREATED"), 400)["code"].asString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["PREPARING", "CANCELLED"])
    fun `only one concurrent update from the same status succeeds`(competingStatus: String) {
        val original = createOrder()
        val id = original["id"].asString()
        Executors.newFixedThreadPool(2).use { executor ->
            val pending = context.getBean(DataSource::class.java).connection.use { blocker ->
                blocker.autoCommit = false
                try {
                    // Hold only the writes: both HTTP requests can read CREATED before either updates it.
                    blocker.prepareStatement("SELECT id FROM orders WHERE id = ? FOR UPDATE").use { statement ->
                        statement.setObject(1, UUID.fromString(id))
                        statement.executeQuery().use { assertTrue(it.next()) }
                    }
                    val requests = listOf("PREPARING", competingStatus).map { status ->
                        executor.submit<HttpResponse<String>> { changeStatus(id, status, "CREATED") }
                    }
                    awaitBlockedUpdates()
                    requests
                } finally {
                    blocker.rollback()
                }
            }
            val responses = pending.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(listOf(200, 409), responses.map { it.statusCode() }.sorted())
            val winner = body(responses.single { it.statusCode() == 200 }, 200)
            val loser = body(responses.single { it.statusCode() == 409 }, 409)
            assertEquals("ORDER_STATUS_CONFLICT", loser["code"].asString())
            assertTrue(winner["status"].asString() in setOf("PREPARING", "CANCELLED"))
            assertEquals(original["items"], winner["items"])
            assertEquals(original["totalPriceInKopecks"], winner["totalPriceInKopecks"])
            assertEquals(winner, getOrder(id))
        }
    }

    private fun awaitBlockedUpdates() {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (System.nanoTime() < deadline) {
            val blocked = jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE 'UPDATE orders%'
                """.trimIndent(),
                Int::class.java,
            )!!
            if (blocked == 2) return
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos())
        }
        throw AssertionError("Both status requests must reach the conditional UPDATE before the lock is released")
    }

    private fun startApplication(): ConfigurableApplicationContext =
        SpringApplicationBuilder(TastyAndTochkaApplication::class.java).run(
            "--spring.datasource.url=${postgres.jdbcUrl}",
            "--spring.datasource.username=${postgres.username}",
            "--spring.datasource.password=${postgres.password}",
            "--server.port=0",
        )

    private fun createOrder(): JsonNode = body(
        client.send(
            HttpRequest.newBuilder(uri("/api/v1/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    """{"items":[{"productId":"20000000-0000-0000-0000-000000000001","quantity":2}]}""",
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        ),
        201,
    )

    private fun getOrder(id: String): JsonNode = body(
        client.send(HttpRequest.newBuilder(uri("/api/v1/orders/$id")).GET().build(), HttpResponse.BodyHandlers.ofString()),
        200,
    )

    private fun getJson(path: String): JsonNode = body(
        client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString()),
        200,
    )

    private fun changeStatus(id: String, status: String, expectedStatus: String): HttpResponse<String> =
        patch(id, """{"status":"$status","expectedStatus":"$expectedStatus"}""")

    private fun patch(id: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(uri("/api/v1/orders/$id/status"))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun uri(path: String): URI =
        URI.create("http://localhost:${context.environment.getRequiredProperty("local.server.port")}$path")

    private fun body(response: HttpResponse<String>, expectedStatus: Int): JsonNode {
        assertEquals(expectedStatus, response.statusCode(), response.body())
        return json.readTree(response.body())
    }
}
