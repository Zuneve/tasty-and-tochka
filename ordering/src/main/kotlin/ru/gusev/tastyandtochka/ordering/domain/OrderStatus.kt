package ru.gusev.tastyandtochka.ordering.domain

enum class OrderStatus {
    CREATED,
    PREPARING,
    READY,
    COMPLETED,
    CANCELLED;

    fun canTransitionTo(newStatus: OrderStatus): Boolean = when (this) {
        CREATED -> newStatus == PREPARING || newStatus == CANCELLED
        PREPARING -> newStatus == READY
        READY -> newStatus == COMPLETED
        COMPLETED, CANCELLED -> false
    }
}
