package com.thridify.infrastructure.outbox

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxPublisher(private val publish: OutboxRelay) {
    @Scheduled(fixedDelay = 500)
    fun pollAndPublish() = publish.execute()
}
