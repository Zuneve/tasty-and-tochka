package ru.gusev.tastyandtochka.catalog.infrastructure.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import ru.gusev.tastyandtochka.catalog.application.CatalogRepository
import ru.gusev.tastyandtochka.catalog.domain.Category
import ru.gusev.tastyandtochka.catalog.domain.Product
import java.sql.ResultSet
import java.util.UUID

@Repository
internal class PostgresCatalogRepository(private val jdbc: JdbcClient) : CatalogRepository {
    override fun findCategories(): List<Category> = jdbc
        .sql("SELECT id, name FROM categories ORDER BY name, id")
        .query { row, _ ->
            Category(
                id = row.getObject("id", UUID::class.java),
                name = row.getString("name"),
            )
        }
        .list()

    override fun findProductsByCategoryId(categoryId: UUID?): List<Product> {
        val query = if (categoryId == null) {
            jdbc.sql("$productSelect ORDER BY name, id")
        } else {
            jdbc.sql("$productSelect WHERE category_id = :categoryId ORDER BY name, id")
                .param("categoryId", categoryId)
        }
        return query.query { row, _ -> row.toProduct() }.list()
    }

    override fun findProductsByIds(ids: Set<UUID>): List<Product> {
        if (ids.isEmpty()) return emptyList()

        return jdbc.sql("$productSelect WHERE id IN (:ids) ORDER BY name, id")
            .param("ids", ids)
            .query { row, _ -> row.toProduct() }
            .list()
    }

    private fun ResultSet.toProduct() = Product(
        id = getObject("id", UUID::class.java),
        categoryId = getObject("category_id", UUID::class.java),
        name = getString("name"),
        description = getString("description"),
        priceInKopecks = getLong("price_in_kopecks"),
        available = getBoolean("available"),
    )

    private val productSelect =
        "SELECT id, category_id, name, description, price_in_kopecks, available FROM products"
}
