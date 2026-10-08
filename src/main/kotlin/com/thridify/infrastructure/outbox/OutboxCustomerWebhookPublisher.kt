package com.thridify.infrastructure.outbox
import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.generation.CustomerWebhookPublisher
import com.thridify.domain.generation.JobNotification
import org.springframework.stereotype.Component
data class CustomerWebhookMessage(val url: String, val notification: JobNotification)

@Component
class OutboxCustomerWebhookPublisher(private val outbox: OutboxRepository, private val mapper: ObjectMapper) : CustomerWebhookPublisher {
    override fun publish(url: String, notification: JobNotification) {
        outbox.insert("CUSTOMER_WEBHOOK", notification.jobId, mapper.writeValueAsString(CustomerWebhookMessage(url, notification)))
    }
}
