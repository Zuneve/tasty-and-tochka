package ru.gusev.tastyandtochka.ordering.domain

class InvalidOrderStatusTransitionException(from: OrderStatus, to: OrderStatus) :
    IllegalStateException("Cannot change order status from $from to $to")
