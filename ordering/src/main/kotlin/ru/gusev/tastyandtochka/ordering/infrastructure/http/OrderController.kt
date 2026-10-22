package ru.gusev.tastyandtochka.ordering.infrastructure.http

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.gusev.tastyandtochka.ordering.api.InvalidOrderException
import ru.gusev.tastyandtochka.ordering.application.ChangeOrderStatusCommand
import ru.gusev.tastyandtochka.ordering.application.CreateOrderCommand
import ru.gusev.tastyandtochka.ordering.application.OrderService
import ru.gusev.tastyandtochka.ordering.application.RequestedOrderItem
import reactor.core.publisher.Mono
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val service: OrderService) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateOrderRequest): Mono<ResponseEntity<OrderResponse>> {
        val items = ArrayList<RequestedOrderItem>(request.items.size)
        for (item in request.items) {
            if (item == null) {
                throw InvalidOrderException("Order items must not be null")
            }
            items.add(RequestedOrderItem(item.productId, item.quantity))
        }
        return service.create(CreateOrderCommand(items)).map { order ->
            ResponseEntity.created(URI.create("/api/v1/orders/${order.id}")).body(order.toResponse())
        }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable(name = "id") id: UUID): Mono<OrderResponse> {
        return service.get(id).map { order -> order.toResponse() }
    }

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable(name = "id") id: UUID,
        @Valid @RequestBody request: ChangeOrderStatusRequest,
    ): Mono<OrderResponse> {
        val expectedStatus = request.expectedStatus
        if (expectedStatus == null) {
            throw InvalidOrderException("Expected order status is required")
        }
        val command = ChangeOrderStatusCommand(request.status, expectedStatus)
        return service.changeStatus(id, command).map { order -> order.toResponse() }
    }
}
