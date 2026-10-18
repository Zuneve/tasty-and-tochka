package ru.gusev.tastyandtochka.ordering.application

import java.util.UUID

internal data class CreateOrderCommand(val items: List<RequestedOrderItem>)

internal data class RequestedOrderItem(val productId: UUID, val quantity: Int)
