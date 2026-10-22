package ru.gusev.tastyandtochka.catalog.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.gusev.tastyandtochka.catalog.api.CatalogQuery
import ru.gusev.tastyandtochka.catalog.api.ProductForOrder
import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import reactor.core.publisher.Mono
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CatalogService(private val repository: CatalogRepository) : CatalogQuery {
    fun listCategories(): Mono<List<Category>> {
        return repository.findCategories()
    }

    fun listProducts(categoryId: UUID?): Mono<List<Product>> {
        return repository.findProductsByCategoryId(categoryId)
    }

    override fun findProducts(ids: Set<UUID>): Mono<List<ProductForOrder>> {
        if (ids.isEmpty()) {
            return Mono.just(emptyList())
        }
        return repository.findProductsByIds(ids).map { products ->
            val result = ArrayList<ProductForOrder>(products.size)
            for (product in products) {
                result.add(ProductForOrder(product.id, product.name, product.priceInKopecks, product.available))
            }
            result
        }
    }
}
