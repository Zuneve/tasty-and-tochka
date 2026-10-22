package ru.gusev.tastyandtochka.catalog.infrastructure.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import ru.gusev.tastyandtochka.catalog.application.CatalogRepository
import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class PostgresCatalogRepository(private val template: R2dbcEntityTemplate) : CatalogRepository {
    override fun findCategories(): Mono<List<Category>> {
        val query = Query.empty().sort(Sort.by("name", "id"))
        return template.select(CategoryRow::class.java).matching(query).all().collectList().map { rows ->
            val categories = ArrayList<Category>(rows.size)
            for (row in rows) {
                categories.add(Category(row.id, row.name))
            }
            categories
        }
    }

    override fun findProductsByCategoryId(categoryId: UUID?): Mono<List<Product>> {
        val query = if (categoryId == null) {
            Query.empty()
        } else {
            Query.query(Criteria.where("categoryId").`is`(categoryId))
        }
        return findProducts(query)
    }

    override fun findProductsByIds(ids: Set<UUID>): Mono<List<Product>> {
        if (ids.isEmpty()) {
            return Mono.just(emptyList())
        }
        return findProducts(Query.query(Criteria.where("id").`in`(ids)))
    }

    private fun findProducts(query: Query): Mono<List<Product>> {
        val sortedQuery = query.sort(Sort.by("name", "id"))
        return template.select(ProductRow::class.java).matching(sortedQuery).all().collectList().map { rows ->
            val products = ArrayList<Product>(rows.size)
            for (row in rows) {
                products.add(Product(
                    id = row.id,
                    categoryId = row.categoryId,
                    name = row.name,
                    description = row.description,
                    priceInKopecks = row.priceInKopecks,
                    available = row.available,
                ))
            }
            products
        }
    }
}

@Table("categories")
private data class CategoryRow(@Id val id: UUID, val name: String)

@Table("products")
private data class ProductRow(
    @Id val id: UUID,
    val categoryId: UUID,
    val name: String,
    val description: String?,
    val priceInKopecks: Long,
    val available: Boolean,
)
