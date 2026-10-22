package ru.gusev.tastyandtochka.ordering.application

import ru.gusev.tastyandtochka.ordering.domain.OrderStatus

internal data class ChangeOrderStatusCommand(val status: OrderStatus, val expectedStatus: OrderStatus)
