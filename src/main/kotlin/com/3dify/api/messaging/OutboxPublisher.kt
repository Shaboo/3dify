package com.omni3d.api.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.api.metrics.AppMetrics
import com.omni3d.api.repository.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxPublisher(
    private val outboxRepository: OutboxRepository,
    private val taskProducer: TaskProducer,
    private val objectMapper: ObjectMapper,
    private val metrics: AppMetrics
) {
    private val log = LoggerFactory.getLogger(OutboxPublisher::class.java)

    @Scheduled(fixedDelay = 500)
    fun pollAndPublish() {
        val messages = outboxRepository.findUnpublished(50)
        if (messages.isEmpty()) return

        log.debug("Outbox poll: found {} unpublished message(s)", messages.size)

        for (msg in messages) {
            try {
                val taskMessage = objectMapper.readValue(msg.payload, TaskProducer.TaskMessage::class.java)
                taskProducer.sendTask(taskMessage.jobId, taskMessage.inputImage1Key, taskMessage.inputImage2Key)
                outboxRepository.markPublished(msg.id)
                metrics.outboxPublished.increment()
                log.debug("Outbox message published [msgId={}, jobId={}]", msg.id, taskMessage.jobId)
            } catch (ex: Exception) {
                metrics.outboxFailed.increment()
                log.error("Failed to publish outbox message [msgId={}]: {}", msg.id, ex.message, ex)
            }
        }
    }
}
