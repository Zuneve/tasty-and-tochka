package ru.gusev.tastyandtochka.catalog.domain

import java.util.UUID

data class Category(
    val id: UUID,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
    }
}
