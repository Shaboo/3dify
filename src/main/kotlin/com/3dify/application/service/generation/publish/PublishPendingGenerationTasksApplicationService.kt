package com.`3dify`.application.service.generation.publish

import com.`3dify`.domain.generation.GenerationTaskDelivery
import com.`3dify`.domain.outbox.OutboxRepository
import com.`3dify`.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PublishPendingGenerationTasksApplicationService(
    private val outbox: OutboxRepository,
    private val delivery: GenerationTaskDelivery,
    private val metrics: AppMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (message in outbox.findUnpublished(50)) {
            try {
                val jobId = delivery.deliver(message)
                outbox.markPublished(message.id)
                metrics.outboxPublished.increment()
                log.debug("Outbox message published [msgId={}, jobId={}]", message.id, jobId)
            } catch (ex: Exception) {
                metrics.outboxFailed.increment()
                log.error("Failed to publish outbox message [msgId={}]: {}", message.id, ex.message, ex)
            }
        }
    }
}
