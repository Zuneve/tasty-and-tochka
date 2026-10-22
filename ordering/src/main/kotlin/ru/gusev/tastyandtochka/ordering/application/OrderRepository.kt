package ru.gusev.tastyandtochka.ordering.application

import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderStatus
import java.util.UUID

internal interface OrderRepository {
    fun save(order: Order)
    fun findById(id: UUID): Order?
    fun updateStatus(order: Order, expectedStatus: OrderStatus): Boolean
}
