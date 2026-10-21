package ru.gusev.tastyandtochka

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
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
import javax.sql.DataSource

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiErrorHandlingTest {
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
        context = SpringApplicationBuilder(TastyAndTochkaApplication::class.java).run(
            "--spring.datasource.url=${postgres.jdbcUrl}",
            "--spring.datasource.username=${postgres.username}",
            "--spring.datasource.password=${postgres.password}",
            "--server.port=0",
        )
    }

    @AfterAll
    fun stop() {
        if (::context.isInitialized) context.close()
    }

    @ParameterizedTest
    @CsvSource(
        "GET, /api/v1/products?categoryId=not-a-uuid, 400, INVALID_REQUEST",
        "GET, /api/v1/orders/not-a-uuid, 400, INVALID_REQUEST",
        "GET, /api/v1/orders/00000000-0000-0000-0000-000000000000, 404, ORDER_NOT_FOUND",
        "GET, /api/v1/unknown-resource, 404, NOT_FOUND",
        "DELETE, /api/v1/products, 405, METHOD_NOT_ALLOWED",
        "PUT, /api/v1/orders, 405, METHOD_NOT_ALLOWED",
        "POST, /api/v1/orders, 400, INVALID_REQUEST",
    )
    fun `returns consistent JSON errors for catalog orders and unknown resources`(
        method: String, path: String, status: Int, code: String,
    ) {
        val response = request(method, path, "{")

        val error = assertError(response, status, code)

        assertEquals(setOf("code", "message", "productIds"), error.propertyNames().toSet())
        assertTrue(error["message"].asString().isNotBlank())
    }

    @Test
    fun `preserves the Allow header for unsupported methods`() {
        val response = request("POST", "/api/v1/categories", "{}")

        assertError(response, 405, "METHOD_NOT_ALLOWED")
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"))
    }

    @Test
    fun `preserves supported media types for an unsupported Content-Type`() {
        val response = request("POST", "/api/v1/orders", validOrder(), contentType = "text/plain")

        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE")
        assertTrue(response.headers().firstValue("Accept").orElse("").contains("application/json"))
    }

    @Test
    fun `returns not acceptable instead of an internal error for unsupported response formats`() {
        val response = request("GET", "/api/v1/products", accept = "application/xml")

        assertError(response, 406, "NOT_ACCEPTABLE")
    }

    @Test
    fun `validation errors identify the invalid field without exposing internals`() {
        val response = request(
            "POST", "/api/v1/orders",
            """{"items":[{"productId":"20000000-0000-0000-0000-000000000001","quantity":0}]}""",
        )

        val error = assertError(response, 400, "INVALID_REQUEST")

        assertTrue(error["message"].asString().contains("quantity"))
    }

    @Test
    fun `logs committed creation and status changes with order identifiers`() {
        captureOperations { events ->
            val response = request("POST", "/api/v1/orders", validOrder())
            assertEquals(201, response.statusCode(), response.body())
            val id = json.readTree(response.body())["id"].asString()
            val updated = request("PATCH", "/api/v1/orders/$id/status", """{"status":"PREPARING","version":0}""")
            assertEquals(200, updated.statusCode(), updated.body())

            assertEquals(2, events.list.size)
            assertTrue(events.list.all { it.level == Level.INFO && it.formattedMessage.contains(id) })
            assertTrue(events.list[0].formattedMessage.contains("totalPriceInKopecks="))
            assertTrue(events.list[1].formattedMessage.contains("from=CREATED to=PREPARING version=1"))
        }
    }

    @Test
    fun `does not log success when commit fails and rolls back creation or status changes`() {
        val originalResponse = request("POST", "/api/v1/orders", validOrder())
        assertEquals(201, originalResponse.statusCode())
        val original = json.readTree(originalResponse.body())
        val id = original["id"].asString()
        val ordersBefore = count("orders")
        val itemsBefore = count("order_items")
        jdbc.execute(
            """
            CREATE FUNCTION test_reject_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN
                RAISE EXCEPTION 'test commit failure';
            END;
            ${'$'}${'$'}
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE CONSTRAINT TRIGGER test_reject_commit
            AFTER INSERT OR UPDATE ON orders DEFERRABLE INITIALLY DEFERRED
            FOR EACH ROW EXECUTE FUNCTION test_reject_commit()
            """.trimIndent(),
        )
        try {
            captureOperations { events ->
                assertError(request("POST", "/api/v1/orders", validOrder()), 500, "INTERNAL_ERROR")
                assertError(
                    request("PATCH", "/api/v1/orders/$id/status", """{"status":"PREPARING","version":0}"""),
                    500, "INTERNAL_ERROR",
                )

                assertTrue(events.list.isEmpty(), "Rolled-back operations must not produce success logs")
                assertEquals(ordersBefore, count("orders"))
                assertEquals(itemsBefore, count("order_items"))
                assertEquals(original, json.readTree(request("GET", "/api/v1/orders/$id").body()))
            }
        } finally {
            jdbc.execute("DROP TRIGGER test_reject_commit ON orders")
            jdbc.execute("DROP FUNCTION test_reject_commit()")
        }
    }

    private fun captureOperations(check: (ListAppender<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger("ru.gusev.tastyandtochka.ordering.infrastructure.logging.OrderOperationLogger") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            check(appender)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun assertError(response: HttpResponse<String>, status: Int, code: String): JsonNode {
        assertEquals(status, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        assertFalse(response.body().contains("test commit failure"))
        val error = json.readTree(response.body())
        assertEquals(code, error["code"].asString())
        assertFalse(error.has("trace"))
        assertFalse(error.has("exception"))
        return error
    }

    private fun validOrder() =
        """{"items":[{"productId":"20000000-0000-0000-0000-000000000001","quantity":2}]}"""

    private fun count(table: String): Long = jdbc.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String = "application/json",
        accept: String = "application/json",
    ): HttpResponse<String> {
        val port = context.environment.getRequiredProperty("local.server.port")
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", contentType)
            .header("Accept", accept)
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
