package ru.gusev.tastyandtochka.ordering.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.gusev.tastyandtochka.catalog.domain.Product
import java.time.Instant
import java.util.UUID

class OrderTest {
    @Test
    fun `restores persisted status and timestamp`() {
        val id = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-18T12:00:00Z")
        val order = Order.restore(id, listOf(item()), createdAt, OrderStatus.READY)

        assertEquals(id, order.id)
        assertEquals(createdAt, order.createdAt)
        assertEquals(OrderStatus.READY, order.status)
    }

    @Test
    fun `new order starts with CREATED status`() {
        assertEquals(OrderStatus.CREATED, order().status)
    }

    @Test
    fun `changes a restored order status`() {
        val order = Order.restore(UUID.randomUUID(), listOf(item()), Instant.now(), OrderStatus.READY)

        order.changeStatus(OrderStatus.COMPLETED)

        assertEquals(OrderStatus.COMPLETED, order.status)
    }

    @Test
    fun `calculates total from multiple items`() {
        val order = order(listOf(item(price = 19900, quantity = 2), item(price = 9900)))

        assertEquals(49700L, order.totalPriceInKopecks)
    }

    @Test
    fun `rejects empty order`() {
        assertThrows(IllegalArgumentException::class.java) { order(emptyList()) }
    }

    @Test
    fun `rejects duplicate products`() {
        val item = item()

        assertThrows(IllegalArgumentException::class.java) { order(listOf(item, item.copy(quantity = 2))) }
    }

    @Test
    fun `copies source items and prevents modifying the exposed list`() {
        val originalItem = item()
        val sourceItems = mutableListOf(originalItem)
        val order = order(sourceItems)

        sourceItems.clear()

        assertEquals(listOf(originalItem), order.items)
        assertThrows(UnsupportedOperationException::class.java) {
            (order.items as MutableList<OrderItem>).clear()
        }
        assertEquals(originalItem.totalPriceInKopecks, order.totalPriceInKopecks)
    }

    @Test
    fun `keeps item price and name when the catalog product changes`() {
        var product = Product(UUID.randomUUID(), UUID.randomUUID(), "Burger", null, 19900, true)
        val order = order(listOf(OrderItem(product.id, product.name, product.priceInKopecks, 2)))

        product = product.copy(name = "New burger", priceInKopecks = 24900, available = false)

        assertEquals(24900L, product.priceInKopecks)
        assertEquals("Burger", order.items.single().productName)
        assertEquals(19900L, order.items.single().unitPriceInKopecks)
        assertEquals(39800L, order.totalPriceInKopecks)
    }

    @Test
    fun `rejects order total overflow`() {
        assertThrows(ArithmeticException::class.java) {
            order(listOf(item(price = Long.MAX_VALUE), item(price = 1)))
        }
    }

    @Test
    fun `rejects skipping preparation and keeps the previous status`() {
        val order = order()

        assertThrows(IllegalStateException::class.java) { order.changeStatus(OrderStatus.READY) }

        assertEquals(OrderStatus.CREATED, order.status)
    }

    @Test
    fun `validates all status transitions including repeated and terminal statuses`() {
        val allowedTransitions = setOf(
            OrderStatus.CREATED to OrderStatus.PREPARING,
            OrderStatus.CREATED to OrderStatus.CANCELLED,
            OrderStatus.PREPARING to OrderStatus.READY,
            OrderStatus.READY to OrderStatus.COMPLETED,
        )

        for (from in OrderStatus.entries) {
            for (to in OrderStatus.entries) {
                val order = orderWithStatus(from)
                if ((from to to) in allowedTransitions) {
                    order.changeStatus(to)
                    assertEquals(to, order.status)
                } else {
                    assertThrows(InvalidOrderStatusTransitionException::class.java, { order.changeStatus(to) }, "$from -> $to")
                    assertEquals(from, order.status)
                }
            }
        }
    }

    private fun orderWithStatus(status: OrderStatus): Order {
        val order = order()
        val transitions = when (status) {
            OrderStatus.CREATED -> emptyList()
            OrderStatus.PREPARING -> listOf(OrderStatus.PREPARING)
            OrderStatus.READY -> listOf(OrderStatus.PREPARING, OrderStatus.READY)
            OrderStatus.COMPLETED -> listOf(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED)
            OrderStatus.CANCELLED -> listOf(OrderStatus.CANCELLED)
        }
        transitions.forEach(order::changeStatus)
        return order
    }

    private fun order(items: List<OrderItem> = listOf(item())) =
        Order(UUID.randomUUID(), items, Instant.parse("2026-10-18T12:00:00Z"))

    private fun item(price: Long = 19900, quantity: Int = 1) =
        OrderItem(UUID.randomUUID(), "Burger", price, quantity)
}
