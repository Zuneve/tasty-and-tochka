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
import ru.gusev.tastyandtochka.ordering.application.CreateOrderCommand
import ru.gusev.tastyandtochka.ordering.application.ChangeOrderStatusCommand
import ru.gusev.tastyandtochka.ordering.api.InvalidOrderException
import ru.gusev.tastyandtochka.ordering.application.OrderService
import ru.gusev.tastyandtochka.ordering.application.RequestedOrderItem
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
internal class OrderController(private val service: OrderService) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> {
        val command = CreateOrderCommand(request.items.map { item ->
            if (item == null) throw InvalidOrderException("Order items must not be null")
            RequestedOrderItem(item.productId, item.quantity)
        })
        val order = service.create(command)
        return ResponseEntity.created(URI.create("/api/v1/orders/${order.id}")).body(order.toResponse())
    }

    @GetMapping("/{id}")
    fun get(@PathVariable(name = "id") id: UUID): OrderResponse = service.get(id).toResponse()

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable(name = "id") id: UUID,
        @Valid @RequestBody request: ChangeOrderStatusRequest,
    ): OrderResponse = service.changeStatus(
        id,
        ChangeOrderStatusCommand(
            status = request.status,
            expectedStatus = request.expectedStatus ?: throw InvalidOrderException("Expected order status is required"),
        ),
    ).toResponse()
}
