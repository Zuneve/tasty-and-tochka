package ru.gusev.tastyandtochka

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import javax.sql.DataSource

@Testcontainers
class TastyAndTochkaApplicationTests {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @Test
    fun `upgrades an existing database and preserves data on application restart`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'").use { rows ->
                    rows.next()
                    assertEquals(0, rows.getInt(1))
                }
            }
        }

        val firstChangeLog = startApplication("classpath:db/changelog/db.changelog-before-status-lock.yaml").use { context ->
            val jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
            assertMenu(jdbc, expectedChangeSets = 3)
            assertEquals(0, count(jdbc, "orders"))
            assertEquals(0, count(jdbc, "order_items"))
            verifyConstraintsAndStatus(jdbc)
            assertEquals(0L, jdbc.queryForObject("SELECT version FROM orders", Long::class.java))
            changeLog(jdbc)
        }

        val upgradedChangeLog = startApplication().use { context ->
            val jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
            assertMenu(jdbc)
            assertEquals(firstChangeLog, changeLog(jdbc).take(firstChangeLog.size))
            assertEquals(1, count(jdbc, "orders"))
            assertEquals(1, count(jdbc, "order_items"))
            assertEquals("PREPARING", jdbc.queryForObject("SELECT status FROM orders", String::class.java))
            assertEquals(19900L, jdbc.queryForObject("SELECT total_price_in_kopecks FROM orders", Long::class.java))
            assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'orders' AND column_name = 'version'",
                Int::class.java,
            ))
            changeLog(jdbc)
        }

        startApplication().use { context ->
            val jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
            assertEquals(upgradedChangeLog, changeLog(jdbc))
            assertEquals(1, count(jdbc, "orders"))
            assertEquals(1, count(jdbc, "order_items"))
            assertEquals("PREPARING", jdbc.queryForObject("SELECT status FROM orders", String::class.java))
        }
    }

    private fun startApplication(
        changeLog: String = "classpath:db/changelog/db.changelog-master.yaml",
    ): ConfigurableApplicationContext =
        SpringApplicationBuilder(TastyAndTochkaApplication::class.java).run(
            "--spring.datasource.url=${postgres.jdbcUrl}",
            "--spring.datasource.username=${postgres.username}",
            "--spring.datasource.password=${postgres.password}",
            "--spring.liquibase.change-log=$changeLog",
            "--server.port=0",
        )

    private fun assertMenu(jdbc: JdbcTemplate, expectedChangeSets: Int = 4) {
        assertEquals(3, count(jdbc, "categories"))
        assertEquals(6, count(jdbc, "products"))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products WHERE NOT available", Int::class.java))
        assertEquals(expectedChangeSets, count(jdbc, "databasechangelog"))
    }

    private fun verifyConstraintsAndStatus(jdbc: JdbcTemplate) {
        val orderId = "30000000-0000-0000-0000-000000000001"
        val productId = "20000000-0000-0000-0000-000000000002"
        jdbc.update("INSERT INTO orders (id, total_price_in_kopecks) VALUES ('$orderId', 19900)")
        jdbc.update(
            """
            INSERT INTO order_items (order_id, product_id, product_name, unit_price_in_kopecks, quantity)
            VALUES ('$orderId', '$productId', 'Двойной чизбургер', 19900, 1)
            """.trimIndent(),
        )
        assertEquals("CREATED", jdbc.queryForObject("SELECT status FROM orders", String::class.java))

        // A stale status must not overwrite an order that another request has changed.
        val update = "UPDATE orders SET status = ? WHERE id = '$orderId' AND status = 'CREATED'"
        assertEquals(1, jdbc.update(update, "PREPARING"))
        assertEquals(0, jdbc.update(update, "CANCELLED"))
        assertEquals("PREPARING", jdbc.queryForObject("SELECT status FROM orders", String::class.java))

        val rejectedStatements = listOf(
            "UPDATE categories SET name = E' \\t\\n'",
            "UPDATE products SET name = '' WHERE id = '$productId'",
            "UPDATE products SET price_in_kopecks = -1 WHERE id = '$productId'",
            "UPDATE products SET category_id = '00000000-0000-0000-0000-000000000000' WHERE id = '$productId'",
            "UPDATE orders SET status = 'UNKNOWN' WHERE id = '$orderId'",
            "UPDATE orders SET total_price_in_kopecks = -1 WHERE id = '$orderId'",
            "UPDATE order_items SET quantity = 0 WHERE order_id = '$orderId'",
            "UPDATE order_items SET quantity = 100 WHERE order_id = '$orderId'",
            "UPDATE order_items SET unit_price_in_kopecks = -1 WHERE order_id = '$orderId'",
            "UPDATE order_items SET product_name = ' ' WHERE order_id = '$orderId'",
            "UPDATE order_items SET order_id = '00000000-0000-0000-0000-000000000000' WHERE order_id = '$orderId'",
            "UPDATE order_items SET product_id = '00000000-0000-0000-0000-000000000000' WHERE order_id = '$orderId'",
            """
            INSERT INTO order_items (order_id, product_id, product_name, unit_price_in_kopecks, quantity)
            VALUES ('$orderId', '$productId', 'Повторный товар', 19900, 1)
            """.trimIndent(),
            "DELETE FROM products WHERE id = '$productId'",
        )
        for (sql in rejectedStatements) {
            assertThrows(DataIntegrityViolationException::class.java, { jdbc.update(sql) }, sql)
        }
    }

    private fun count(jdbc: JdbcTemplate, table: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    private fun changeLog(jdbc: JdbcTemplate): List<Map<String, Any?>> =
        jdbc.queryForList("SELECT id, author, filename, md5sum, dateexecuted FROM databasechangelog ORDER BY orderexecuted")
}
