package ru.gusev.tastyandtochka.catalog.infrastructure.http

import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import java.util.UUID

data class CategoryResponse(
    val id: UUID,
    val name: String,
)

data class ProductResponse(
    val id: UUID,
    val categoryId: UUID,
    val name: String,
    val description: String?,
    val priceInKopecks: Long,
    val available: Boolean,
)

fun Category.toResponse() = CategoryResponse(id = id, name = name)

fun Product.toResponse() = ProductResponse(
    id = id,
    categoryId = categoryId,
    name = name,
    description = description,
    priceInKopecks = priceInKopecks,
    available = available,
)
