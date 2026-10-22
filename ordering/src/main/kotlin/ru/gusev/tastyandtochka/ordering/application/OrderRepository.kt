package ru.gusev.tastyandtochka.ordering.application

import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderStatus
import reactor.core.publisher.Mono
import java.util.UUID

interface OrderRepository {
    fun save(order: Order): Mono<Void>
    fun findById(id: UUID): Mono<Order>
    fun updateStatus(order: Order, expectedStatus: OrderStatus): Mono<Boolean>
}
