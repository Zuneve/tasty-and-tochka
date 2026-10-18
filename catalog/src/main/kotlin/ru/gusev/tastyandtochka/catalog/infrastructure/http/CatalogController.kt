package ru.gusev.tastyandtochka.catalog.infrastructure.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.gusev.tastyandtochka.catalog.application.CatalogService
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
internal class CatalogController(private val service: CatalogService) {
    @GetMapping("/categories")
    fun categories(): List<CategoryResponse> = service.listCategories().map { it.toResponse() }

    @GetMapping("/products")
    fun products(@RequestParam(name = "categoryId", required = false) categoryId: UUID?): List<ProductResponse> =
        service.listProducts(categoryId).map { it.toResponse() }
}
