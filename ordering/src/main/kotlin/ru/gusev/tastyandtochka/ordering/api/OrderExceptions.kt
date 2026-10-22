package ru.gusev.tastyandtochka.ordering.api

import java.util.UUID

class InvalidOrderException(message: String) : RuntimeException(message)

class ProductsNotFoundException(val productIds: Set<UUID>) :
    RuntimeException("Some requested products do not exist")

class ProductsUnavailableException(val productIds: Set<UUID>) :
    RuntimeException("Some requested products are unavailable")

class OrderNotFoundException(val orderId: UUID) : RuntimeException("Order $orderId was not found")

class OrderAmountTooLargeException : RuntimeException("Order amount exceeds the supported range")

class OrderStatusConflictException(orderId: UUID) :
    RuntimeException("Order $orderId status has changed; reload it before retrying")
