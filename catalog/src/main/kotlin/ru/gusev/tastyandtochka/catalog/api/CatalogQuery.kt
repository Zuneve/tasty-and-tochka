package ru.gusev.tastyandtochka.catalog.api

import reactor.core.publisher.Mono
import java.util.UUID

interface CatalogQuery {
    fun findProducts(ids: Set<UUID>): Mono<List<ProductForOrder>>
}