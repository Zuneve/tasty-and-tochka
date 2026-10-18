package ru.gusev.tastyandtochka.ordering.domain

import java.time.Instant
import java.util.Collections
import java.util.UUID

class Order private constructor(
    val id: UUID,
    items: List<OrderItem>,
    val createdAt: Instant,
    status: OrderStatus,
    val version: Long,
) {
    constructor(id: UUID, items: List<OrderItem>, createdAt: Instant) :
        this(id, items, createdAt, OrderStatus.CREATED, 0)

    // Copy and protect the list so callers cannot change an existing order's items.
    val items: List<OrderItem> = Collections.unmodifiableList(items.toList())

    var status: OrderStatus = status
        private set

    init {
        require(version >= 0) { "Order version must not be negative" }
        require(this.items.isNotEmpty()) { "Order must contain at least one item" }
        require(this.items.map { it.productId }.distinct().size == this.items.size) {
            "Order must not contain duplicate products"
        }
    }

    val totalPriceInKopecks: Long = this.items.fold(0L) { total, item ->
        Math.addExact(total, item.totalPriceInKopecks)
    }

    fun changeStatus(newStatus: OrderStatus) {
        check(status.canTransitionTo(newStatus)) {
            "Cannot change order status from $status to $newStatus"
        }
        status = newStatus
    }

    companion object {
        fun restore(
            id: UUID,
            items: List<OrderItem>,
            createdAt: Instant,
            status: OrderStatus,
            version: Long,
        ): Order = Order(id, items, createdAt, status, version)
    }
}
