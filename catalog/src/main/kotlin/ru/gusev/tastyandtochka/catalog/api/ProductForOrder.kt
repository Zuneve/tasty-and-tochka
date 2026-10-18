package ru.gusev.tastyandtochka.catalog.api

import java.util.UUID

data class ProductForOrder(
    val id: UUID,
    val name: String,
    val priceInKopecks: Long,
    val available: Boolean,
)