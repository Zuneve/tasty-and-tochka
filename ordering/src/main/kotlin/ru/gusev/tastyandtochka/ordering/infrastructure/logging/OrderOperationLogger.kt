package ru.gusev.tastyandtochka.ordering.infrastructure.logging

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import ru.gusev.tastyandtochka.ordering.application.OrderCreatedEvent
import ru.gusev.tastyandtochka.ordering.application.OrderStatusChangedEvent

@Component
class OrderOperationLogger {
    private val logger = LoggerFactory.getLogger(OrderOperationLogger::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun created(event: OrderCreatedEvent) {
        logger.info(
            "Order created orderId={} itemCount={} totalPriceInKopecks={}",
            event.orderId, event.itemCount, event.totalPriceInKopecks,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun statusChanged(event: OrderStatusChangedEvent) {
        logger.info(
            "Order status changed orderId={} from={} to={}",
            event.orderId, event.previousStatus, event.status,
        )
    }
}
