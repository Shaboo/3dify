package com.thridify.infrastructure.outbox
import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.generation.CustomerWebhookClient
import com.thridify.shared.metrics.AppMetrics
import org.springframework.stereotype.Component
import java.util.UUID
@Component
class CustomerAndGenerationDelivery(private val rabbit: RabbitGenerationTaskDelivery, private val webhooks: CustomerWebhookClient, private val mapper: ObjectMapper, private val metrics: AppMetrics) : OutboxMessageDelivery {
    override fun deliver(message: OutboxMessageEntity): UUID = when (message.aggregateType) {
        "JOB" -> rabbit.deliver(message)

        "CUSTOMER_WEBHOOK" -> {
            val task = mapper.readValue(message.payload, CustomerWebhookMessage::class.java)
            try {
                webhooks.deliver(task.url, task.notification)
                metrics.webhookDeliveriesSuccess.increment()
            } catch (ex: Exception) {
                metrics.webhookDeliveriesFailed.increment()
                throw ex
            }
            task.notification.jobId
        }

        else -> error("Unknown outbox type: ${message.aggregateType}")
    }
}
