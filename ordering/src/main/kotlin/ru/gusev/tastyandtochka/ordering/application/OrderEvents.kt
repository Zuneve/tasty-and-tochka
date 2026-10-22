package ru.gusev.tastyandtochka.ordering.application

import ru.gusev.tastyandtochka.ordering.domain.OrderStatus
import java.util.UUID

data class OrderCreatedEvent(val orderId: UUID, val itemCount: Int, val totalPriceInKopecks: Long)

data class OrderStatusChangedEvent(
    val orderId: UUID,
    val previousStatus: OrderStatus,
    val status: OrderStatus,
)
