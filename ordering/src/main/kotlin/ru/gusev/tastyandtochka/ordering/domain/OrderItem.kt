package ru.gusev.tastyandtochka.ordering.domain

import java.util.UUID

data class OrderItem(
    val productId: UUID,
    val productName: String,
    val unitPriceInKopecks: Long,
    val quantity: Int,
) {
    init {
        require(quantity in 1..99) { "Quantity must be between 1 and 99" }
        require(productName.isNotBlank()) { "Product name must not be blank" }
        require(unitPriceInKopecks >= 0) { "Unit price must not be negative" }
    }

    val totalPriceInKopecks: Long = Math.multiplyExact(unitPriceInKopecks, quantity.toLong())
}
