package ru.gusev.tastyandtochka.ordering.infrastructure.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Repository
import ru.gusev.tastyandtochka.ordering.application.OrderRepository
import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderItem
import ru.gusev.tastyandtochka.ordering.domain.OrderStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresOrderRepository(private val template: R2dbcEntityTemplate) : OrderRepository {
    override fun save(order: Order): Mono<Void> {
        val row = OrderRow(order.id, order.status.name, order.totalPriceInKopecks, order.createdAt.atOffset(ZoneOffset.UTC))
        val savedItems = Flux.fromIterable(order.items).concatMap { item ->
            val itemRow = OrderItemRow(order.id, item.productId, item.productName, item.unitPriceInKopecks, item.quantity)
            template.insert(itemRow)
        }
        return template.insert(row).thenMany(savedItems).then()
    }

    override fun findById(id: UUID): Mono<Order> {
        val orderQuery = Query.query(Criteria.where("id").`is`(id))
        return template.select(OrderRow::class.java).matching(orderQuery).one().flatMap { row ->
            val itemsQuery = Query.query(Criteria.where("orderId").`is`(id)).sort(Sort.by("productId"))
            template.select(OrderItemRow::class.java).matching(itemsQuery).all().collectList().map { itemRows ->
                val items = ArrayList<OrderItem>(itemRows.size)
                for (item in itemRows) {
                    items.add(OrderItem(item.productId, item.productName, item.unitPriceInKopecks, item.quantity))
                }
                val order = Order.restore(row.id, items, row.createdAt.toInstant(), OrderStatus.valueOf(row.status))
                check(order.totalPriceInKopecks == row.totalPriceInKopecks) { "Stored order total does not match its items" }
                order
            }
        }
    }

    override fun updateStatus(order: Order, expectedStatus: OrderStatus): Mono<Boolean> {
        val condition = Criteria.where("id").`is`(order.id).and("status").`is`(expectedStatus.name)
        val query = Query.query(condition)
        val update = Update.update("status", order.status.name)
        return template.update(OrderRow::class.java).matching(query).apply(update).map { updatedRows ->
            updatedRows == 1L
        }
    }
}

@Table("orders")
private data class OrderRow(
    @Id val id: UUID,
    val status: String,
    val totalPriceInKopecks: Long,
    val createdAt: OffsetDateTime,
)

@Table("order_items")
private data class OrderItemRow(
    val orderId: UUID,
    val productId: UUID,
    val productName: String,
    val unitPriceInKopecks: Long,
    val quantity: Int,
)
