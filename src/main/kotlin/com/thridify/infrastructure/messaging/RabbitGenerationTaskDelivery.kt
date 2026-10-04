package com.thridify.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.generation.GenerationTaskDelivery
import com.thridify.domain.outbox.OutboxMessageEntity
import com.thridify.shared.message.GenerationTaskMessage
import org.springframework.stereotype.Component

@Component
class RabbitGenerationTaskDelivery(private val producer: TaskProducer, private val mapper: ObjectMapper) : GenerationTaskDelivery {
    override fun deliver(message: OutboxMessageEntity): java.util.UUID {
        val task = mapper.readValue(message.payload, GenerationTaskMessage::class.java)
        producer.sendTask(task.jobId, task.inputImage1Key, task.inputImage2Key)
        return task.jobId
    }
}
