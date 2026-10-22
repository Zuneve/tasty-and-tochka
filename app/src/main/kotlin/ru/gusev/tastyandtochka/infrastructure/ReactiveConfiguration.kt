package ru.gusev.tastyandtochka.infrastructure

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.reactive.TransactionalEventPublisher

@Configuration
class ReactiveConfiguration {
    @Bean
    fun transactionalEventPublisher(publisher: ApplicationEventPublisher): TransactionalEventPublisher {
        return TransactionalEventPublisher(publisher)
    }
}
