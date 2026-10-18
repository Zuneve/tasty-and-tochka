package ru.gusev.tastyandtochka.catalog.application

import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import java.util.UUID

internal interface CatalogRepository {
    fun findCategories(): List<Category>
    fun findProductsByCategoryId(categoryId: UUID?): List<Product>
    fun findProductsByIds(ids: Set<UUID>): List<Product>
}
