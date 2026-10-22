package ru.gusev.tastyandtochka.catalog.application

import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import reactor.core.publisher.Mono
import java.util.UUID

interface CatalogRepository {
    fun findCategories(): Mono<List<Category>>
    fun findProductsByCategoryId(categoryId: UUID?): Mono<List<Product>>
    fun findProductsByIds(ids: Set<UUID>): Mono<List<Product>>
}
