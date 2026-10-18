package ru.gusev.tastyandtochka.catalog.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class ProductTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `rejects blank name`(name: String) {
        assertThrows(IllegalArgumentException::class.java) { product(name = name) }
    }

    @Test
    fun `rejects negative price`() {
        assertThrows(IllegalArgumentException::class.java) { product(price = -1) }
    }

    @Test
    fun `allows a free unavailable product to remain in the catalog`() {
        val product = product(price = 0, available = false)

        assertEquals(0L, product.priceInKopecks)
        assertFalse(product.available)
    }

    private fun product(
        name: String = "Burger",
        price: Long = 19900,
        available: Boolean = true,
    ) = Product(UUID.randomUUID(), UUID.randomUUID(), name, null, price, available)
}
