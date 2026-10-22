package ru.gusev.tastyandtochka.ordering.domain

import java.time.Instant
import java.util.Collections
import java.util.UUID

class Order private constructor(
    val id: UUID,
    items: List<OrderItem>,
    val createdAt: Instant,
    status: OrderStatus,
) {
    constructor(id: UUID, items: List<OrderItem>, createdAt: Instant) :
        this(id, items, createdAt, OrderStatus.CREATED)

    // Copy and protect the list so callers cannot change an existing order's items.
    val items: List<OrderItem> = Collections.unmodifiableList(items.toList())

    var status: OrderStatus = status
        private set

    init {
        require(this.items.isNotEmpty()) { "Order must contain at least one item" }
        val productIds = HashSet<UUID>()
        for (item in this.items) {
            require(productIds.add(item.productId)) { "Order must not contain duplicate products" }
        }
    }

    val totalPriceInKopecks: Long = calculateTotal()

    private fun calculateTotal(): Long {
        var total = 0L
        for (item in items) {
            total = Math.addExact(total, item.totalPriceInKopecks)
        }
        return total
    }

    fun changeStatus(newStatus: OrderStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw InvalidOrderStatusTransitionException(status, newStatus)
        }
        status = newStatus
    }

    companion object {
        fun restore(
            id: UUID,
            items: List<OrderItem>,
            createdAt: Instant,
            status: OrderStatus,
        ): Order = Order(id, items, createdAt, status)
    }
}
