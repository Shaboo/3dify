package com.`3dify`.infrastructure.messaging

import com.`3dify`.domain.generation.GenerationTaskDelivery
import com.`3dify`.domain.outbox.OutboxMessageEntity
import com.`3dify`.shared.message.GenerationTaskMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class RabbitGenerationTaskDelivery(private val producer: TaskProducer, private val mapper: ObjectMapper) : GenerationTaskDelivery {
    override fun deliver(message: OutboxMessageEntity): java.util.UUID {
        val task = mapper.readValue(message.payload, GenerationTaskMessage::class.java)
        producer.sendTask(task.jobId, task.inputImage1Key, task.inputImage2Key)
        return task.jobId
    }
}
