package ru.gusev.tastyandtochka.ordering.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class OrderItemTest {
    @Test
    fun `calculates price for all units`() {
        assertEquals(39800L, item(price = 19900, quantity = 2).totalPriceInKopecks)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 99])
    fun `accepts quantity boundaries`(quantity: Int) {
        assertEquals(100L * quantity, item(price = 100, quantity = quantity).totalPriceInKopecks)
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 100])
    fun `rejects quantity outside allowed range`(quantity: Int) {
        assertThrows(IllegalArgumentException::class.java) { item(quantity = quantity) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `rejects blank product name`(name: String) {
        assertThrows(IllegalArgumentException::class.java) { item(name = name) }
    }

    @Test
    fun `rejects negative price`() {
        assertThrows(IllegalArgumentException::class.java) { item(price = -1) }
    }

    @Test
    fun `allows zero price`() {
        assertEquals(0L, item(price = 0).totalPriceInKopecks)
    }

    @Test
    fun `rejects price multiplication overflow`() {
        assertThrows(ArithmeticException::class.java) { item(price = Long.MAX_VALUE, quantity = 2) }
    }

    private fun item(name: String = "Burger", price: Long = 19900, quantity: Int = 1) =
        OrderItem(UUID.randomUUID(), name, price, quantity)
}
