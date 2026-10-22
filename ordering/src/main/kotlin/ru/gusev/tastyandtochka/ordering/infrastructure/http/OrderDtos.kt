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

internal data class CreateOrderRequest(
    @field:NotEmpty
    @field:Size(max = 100)
    @field:Valid
    val items: List<CreateOrderItemRequest?>,
)

internal data class CreateOrderItemRequest(
    val productId: UUID,
    @field:Min(1)
    @field:Max(99)
    val quantity: Int,
)

internal data class ChangeOrderStatusRequest(
    val status: OrderStatus,
    @field:NotNull
    val expectedStatus: OrderStatus?,
)

internal data class OrderResponse(
    val id: UUID,
    val status: String,
    val createdAt: Instant,
    val totalPriceInKopecks: Long,
    val items: List<OrderItemResponse>,
)

internal data class OrderItemResponse(
    val productId: UUID,
    val productName: String,
    val unitPriceInKopecks: Long,
    val quantity: Int,
    val totalPriceInKopecks: Long,
)

internal fun Order.toResponse() = OrderResponse(
    id = id,
    status = status.name,
    createdAt = createdAt,
    totalPriceInKopecks = totalPriceInKopecks,
    items = items.map { item ->
        OrderItemResponse(item.productId, item.productName, item.unitPriceInKopecks, item.quantity, item.totalPriceInKopecks)
    },
)
