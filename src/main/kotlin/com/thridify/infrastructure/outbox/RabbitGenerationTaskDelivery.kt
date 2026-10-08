package com.thridify.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.infrastructure.messaging.TaskProducer
import com.thridify.shared.message.GenerationTaskMessage
import org.springframework.stereotype.Component

@Component
class RabbitGenerationTaskDelivery(private val producer: TaskProducer, private val mapper: ObjectMapper) {
    fun deliver(message: OutboxMessageEntity): java.util.UUID {
        val task = mapper.readValue(message.payload, GenerationTaskMessage::class.java)
        producer.sendTask(task.jobId, task.imageKeys ?: listOf(task.inputImage1Key, task.inputImage2Key))
        return task.jobId
    }
}
