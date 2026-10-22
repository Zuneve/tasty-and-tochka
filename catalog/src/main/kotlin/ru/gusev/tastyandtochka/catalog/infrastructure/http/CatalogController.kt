package ru.gusev.tastyandtochka.catalog.infrastructure.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.gusev.tastyandtochka.catalog.application.CatalogService
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class CatalogController(private val service: CatalogService) {
    @GetMapping("/categories")
    fun categories(): Mono<List<CategoryResponse>> {
        return service.listCategories().map { categories ->
            val response = ArrayList<CategoryResponse>(categories.size)
            for (category in categories) {
                response.add(category.toResponse())
            }
            response
        }
    }

    @GetMapping("/products")
    fun products(@RequestParam(name = "categoryId", required = false) categoryId: UUID?): Mono<List<ProductResponse>> {
        return service.listProducts(categoryId).map { products ->
            val response = ArrayList<ProductResponse>(products.size)
            for (product in products) {
                response.add(product.toResponse())
            }
            response
        }
    }
}
