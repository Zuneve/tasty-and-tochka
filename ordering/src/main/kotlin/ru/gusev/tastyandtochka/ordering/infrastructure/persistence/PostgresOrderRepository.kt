package ru.gusev.tastyandtochka.ordering.infrastructure.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import ru.gusev.tastyandtochka.ordering.application.OrderRepository
import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderItem
import ru.gusev.tastyandtochka.ordering.domain.OrderStatus
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
internal class PostgresOrderRepository(private val jdbc: JdbcClient) : OrderRepository {
    override fun save(order: Order) {
        jdbc.sql(
            """
            INSERT INTO orders (id, status, total_price_in_kopecks, created_at, version)
            VALUES (:id, :status, :total, :createdAt, :version)
            """.trimIndent(),
        )
            .param("id", order.id)
            .param("status", order.status.name)
            .param("total", order.totalPriceInKopecks)
            .param("createdAt", order.createdAt.atOffset(ZoneOffset.UTC))
            .param("version", order.version)
            .update()

        for (item in order.items) {
            jdbc.sql(
                """
                INSERT INTO order_items (order_id, product_id, product_name, unit_price_in_kopecks, quantity)
                VALUES (:orderId, :productId, :productName, :unitPrice, :quantity)
                """.trimIndent(),
            )
                .param("orderId", order.id)
                .param("productId", item.productId)
                .param("productName", item.productName)
                .param("unitPrice", item.unitPriceInKopecks)
                .param("quantity", item.quantity)
                .update()
        }
    }

    override fun findById(id: UUID): Order? {
        val header = jdbc.sql("SELECT id, status, total_price_in_kopecks, created_at, version FROM orders WHERE id = :id")
            .param("id", id)
            .query { row, _ ->
                OrderHeader(
                    id = row.getObject("id", UUID::class.java),
                    status = OrderStatus.valueOf(row.getString("status")),
                    total = row.getLong("total_price_in_kopecks"),
                    createdAt = row.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                    version = row.getLong("version"),
                )
            }
            .optional().orElse(null) ?: return null

        val items = jdbc.sql(
            """
            SELECT product_id, product_name, unit_price_in_kopecks, quantity
            FROM order_items WHERE order_id = :id ORDER BY product_id
            """.trimIndent(),
        )
            .param("id", id)
            .query { row, _ ->
                OrderItem(
                    productId = row.getObject("product_id", UUID::class.java),
                    productName = row.getString("product_name"),
                    unitPriceInKopecks = row.getLong("unit_price_in_kopecks"),
                    quantity = row.getInt("quantity"),
                )
            }
            .list()

        val order = Order.restore(header.id, items, header.createdAt, header.status, header.version)
        check(order.totalPriceInKopecks == header.total) { "Stored order total does not match its items" }
        return order
    }

    private data class OrderHeader(
        val id: UUID,
        val status: OrderStatus,
        val total: Long,
        val createdAt: Instant,
        val version: Long,
    )
}
