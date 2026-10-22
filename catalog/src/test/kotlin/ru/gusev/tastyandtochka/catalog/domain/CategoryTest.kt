package ru.gusev.tastyandtochka.catalog.domain

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class CategoryTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `rejects blank name`(name: String) {
        assertThrows(IllegalArgumentException::class.java) {
            Category(UUID.randomUUID(), name)
        }
    }
}
