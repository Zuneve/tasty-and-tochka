package ru.gusev.tastyandtochka.catalog.api

import java.util.UUID

interface CatalogQuery {
    fun findProducts(ids: Set<UUID>): List<ProductForOrder>
}