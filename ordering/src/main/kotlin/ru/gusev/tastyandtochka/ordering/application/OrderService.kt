package ru.gusev.tastyandtochka.ordering.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.gusev.tastyandtochka.catalog.api.CatalogQuery
import ru.gusev.tastyandtochka.ordering.domain.Order
import ru.gusev.tastyandtochka.ordering.domain.OrderItem
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
internal class OrderService(
    private val catalog: CatalogQuery,
    private val repository: OrderRepository,
) {
    @Transactional
    fun create(command: CreateOrderCommand): Order {
        if (command.items.size !in 1..100) {
            throw InvalidOrderException("Order must contain between 1 and 100 items")
        }
        if (command.items.any { it.quantity !in 1..99 }) {
            throw InvalidOrderException("Quantity must be between 1 and 99")
        }

        val productIds = command.items.map { it.productId }.toSet()
        if (productIds.size != command.items.size) {
            throw InvalidOrderException("Order must not contain duplicate products")
        }

        val products = catalog.findProducts(productIds).associateBy { it.id }
        val missing = productIds - products.keys
        if (missing.isNotEmpty()) throw ProductsNotFoundException(missing)

        val unavailable = products.values.filterNot { it.available }.map { it.id }.toSet()
        if (unavailable.isNotEmpty()) throw ProductsUnavailableException(unavailable)

        val order = try {
            val items = command.items.sortedBy { it.productId.toString() }.map { requested ->
                val product = products.getValue(requested.productId)
                OrderItem(
                    productId = product.id,
                    productName = product.name,
                    unitPriceInKopecks = product.priceInKopecks,
                    quantity = requested.quantity,
                )
            }
            Order(UUID.randomUUID(), items, Instant.now().truncatedTo(ChronoUnit.MICROS))
        } catch (_: ArithmeticException) {
            throw OrderAmountTooLargeException()
        }

        repository.save(order)
        return order
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Order = repository.findById(id) ?: throw OrderNotFoundException(id)
}
