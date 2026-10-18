package ru.gusev.tastyandtochka.catalog.domain

import java.util.UUID

data class Product(
    val id: UUID,
    val categoryId: UUID,
    val name: String,
    val description: String?,
    val priceInKopecks: Long,
    val available: Boolean,
) {
    init {
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(priceInKopecks >= 0) { "Product price must not be negative" }
    }
}
