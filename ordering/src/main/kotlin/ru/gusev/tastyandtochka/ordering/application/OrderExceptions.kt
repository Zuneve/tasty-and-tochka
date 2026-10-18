package ru.gusev.tastyandtochka.ordering.application

import java.util.UUID

internal class InvalidOrderException(message: String) : RuntimeException(message)

internal class ProductsNotFoundException(val productIds: Set<UUID>) :
    RuntimeException("Some requested products do not exist")

internal class ProductsUnavailableException(val productIds: Set<UUID>) :
    RuntimeException("Some requested products are unavailable")

internal class OrderNotFoundException(val orderId: UUID) : RuntimeException("Order $orderId was not found")

internal class OrderAmountTooLargeException : RuntimeException("Order amount exceeds the supported range")
