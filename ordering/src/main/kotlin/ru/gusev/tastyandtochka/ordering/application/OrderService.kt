package ru.gusev.tastyandtochka.ordering.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.reactive.TransactionalEventPublisher
import ru.gusev.tastyandtochka.catalog.api.CatalogQuery
import ru.gusev.tastyandtochka.catalog.api.ProductForOrder
import ru.gusev.tastyandtochka.ordering.api.InvalidOrderException
import ru.gusev.tastyandtochka.ordering.api.OrderAmountTooLargeException
import ru.gusev.tastyandtochka.ordering.api.OrderNotFoundException
import ru.gusev.tastyandtochka.ordering.api.OrderStatusConflictException
import ru.gusev.tastyandtochka.ordering.api.ProductsNotFoundException
import ru.gusev.tastyandtochka.ordering.api.ProductsUnavailableException
import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderItem
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class OrderService(
    private val catalog: CatalogQuery,
    private val repository: OrderRepository,
    private val events: TransactionalEventPublisher,
) {
    @Transactional
    fun create(command: CreateOrderCommand): Mono<Order> {
        return Mono.defer {
            val productIds = validateItems(command.items)
            catalog.findProducts(productIds).flatMap { products ->
                val productsById = checkProducts(productIds, products)
                val order = buildOrder(command.items, productsById)
                val event = OrderCreatedEvent(order.id, order.items.size, order.totalPriceInKopecks)
                repository.save(order)
                    .then(events.publishEvent(event))
                    .thenReturn(order)
            }
        }
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Mono<Order> {
        return repository.findById(id).switchIfEmpty(Mono.error(OrderNotFoundException(id)))
    }

    @Transactional
    fun changeStatus(id: UUID, command: ChangeOrderStatusCommand): Mono<Order> {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(OrderNotFoundException(id)))
            .flatMap { order ->
                if (order.status != command.expectedStatus) {
                    throw OrderStatusConflictException(id)
                }
                val previousStatus = order.status
                order.changeStatus(command.status)
                repository.updateStatus(order, command.expectedStatus).flatMap { updated ->
                    if (!updated) {
                        throw OrderStatusConflictException(id)
                    }
                    val event = OrderStatusChangedEvent(order.id, previousStatus, order.status)
                    events.publishEvent(event).thenReturn(order)
                }
            }
    }

    private fun validateItems(items: List<RequestedOrderItem>): Set<UUID> {
        if (items.isEmpty() || items.size > 100) {
            throw InvalidOrderException("Order must contain between 1 and 100 items")
        }

        val productIds = HashSet<UUID>()
        for (item in items) {
            if (item.quantity < 1 || item.quantity > 99) {
                throw InvalidOrderException("Quantity must be between 1 and 99")
            }
            if (!productIds.add(item.productId)) {
                throw InvalidOrderException("Order must not contain duplicate products")
            }
        }
        return productIds
    }

    private fun checkProducts(ids: Set<UUID>, products: List<ProductForOrder>): Map<UUID, ProductForOrder> {
        val productsById = HashMap<UUID, ProductForOrder>()
        for (product in products) {
            productsById[product.id] = product
        }

        val missingIds = HashSet<UUID>()
        val unavailableIds = HashSet<UUID>()
        for (id in ids) {
            val product = productsById[id]
            if (product == null) {
                missingIds.add(id)
            } else if (!product.available) {
                unavailableIds.add(id)
            }
        }
        if (missingIds.isNotEmpty()) {
            throw ProductsNotFoundException(missingIds)
        }
        if (unavailableIds.isNotEmpty()) {
            throw ProductsUnavailableException(unavailableIds)
        }
        return productsById
    }

    private fun buildOrder(requestedItems: List<RequestedOrderItem>, products: Map<UUID, ProductForOrder>): Order {
        try {
            // Return positions in the same stable order as when reading the order from PostgreSQL.
            val sortedItems = requestedItems.sortedBy { requestedItem -> requestedItem.productId.toString() }
            val items = ArrayList<OrderItem>(sortedItems.size)
            for (requestedItem in sortedItems) {
                val product = products.getValue(requestedItem.productId)
                items.add(OrderItem(product.id, product.name, product.priceInKopecks, requestedItem.quantity))
            }
            return Order(UUID.randomUUID(), items, Instant.now().truncatedTo(ChronoUnit.MICROS))
        } catch (_: ArithmeticException) {
            throw OrderAmountTooLargeException()
        }
    }
}
