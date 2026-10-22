package ru.gusev.tastyandtochka.ordering.application

import ru.gusev.tastyandtochka.ordering.domain.OrderStatus

data class ChangeOrderStatusCommand(val status: OrderStatus, val expectedStatus: OrderStatus)
