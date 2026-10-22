package ru.gusev.tastyandtochka.ordering.application

import java.util.UUID

data class CreateOrderCommand(val items: List<RequestedOrderItem>)

data class RequestedOrderItem(val productId: UUID, val quantity: Int)
