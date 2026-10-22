package ru.gusev.tastyandtochka.ordering.infrastructure.http

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderStatus
import java.time.Instant
import java.util.UUID

data class CreateOrderRequest(
    @field:NotEmpty
    @field:Size(max = 100)
    @field:Valid
    val items: List<CreateOrderItemRequest?>,
)

data class CreateOrderItemRequest(
    val productId: UUID,
    @field:Min(1)
    @field:Max(99)
    val quantity: Int,
)

data class ChangeOrderStatusRequest(
    val status: OrderStatus,
    @field:NotNull
    val expectedStatus: OrderStatus?,
)

data class OrderResponse(
    val id: UUID,
    val status: String,
    val createdAt: Instant,
    val totalPriceInKopecks: Long,
    val items: List<OrderItemResponse>,
)

data class OrderItemResponse(
    val productId: UUID,
    val productName: String,
    val unitPriceInKopecks: Long,
    val quantity: Int,
    val totalPriceInKopecks: Long,
)

fun Order.toResponse(): OrderResponse {
    val responseItems = ArrayList<OrderItemResponse>(items.size)
    for (item in items) {
        responseItems.add(OrderItemResponse(
            productId = item.productId,
            productName = item.productName,
            unitPriceInKopecks = item.unitPriceInKopecks,
            quantity = item.quantity,
            totalPriceInKopecks = item.totalPriceInKopecks,
        ))
    }
    return OrderResponse(id, status.name, createdAt, totalPriceInKopecks, responseItems)
}
