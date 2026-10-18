package ru.gusev.tastyandtochka.catalog.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.gusev.tastyandtochka.catalog.api.CatalogQuery
import ru.gusev.tastyandtochka.catalog.api.ProductForOrder
import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import java.util.UUID

@Service
@Transactional(readOnly = true)
internal class CatalogService(private val repository: CatalogRepository) : CatalogQuery {
    fun listCategories(): List<Category> = repository.findCategories()

    fun listProducts(categoryId: UUID?): List<Product> = repository.findProductsByCategoryId(categoryId)

    override fun findProducts(ids: Set<UUID>): List<ProductForOrder> {
        if (ids.isEmpty()) return emptyList()

        return repository.findProductsByIds(ids).map { product ->
            ProductForOrder(
                id = product.id,
                name = product.name,
                priceInKopecks = product.priceInKopecks,
                available = product.available,
            )
        }
    }
}
