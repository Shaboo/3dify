package com.`3dify`.infrastructure.messaging

import com.`3dify`.domain.generation.GenerationTaskPublisher
import com.`3dify`.domain.outbox.OutboxRepository
import com.`3dify`.shared.message.GenerationTaskMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OutboxGenerationTaskPublisher(private val outbox: OutboxRepository, private val mapper: ObjectMapper) : GenerationTaskPublisher {
    override fun enqueue(jobId: UUID, imageKey1: String, imageKey2: String) {
        outbox.insert("JOB", jobId, mapper.writeValueAsString(GenerationTaskMessage(jobId, imageKey1, imageKey2)))
    }
}
