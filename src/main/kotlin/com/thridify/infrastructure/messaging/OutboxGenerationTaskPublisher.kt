package com.thridify.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.generation.GenerationTaskPublisher
import com.thridify.domain.outbox.OutboxRepository
import com.thridify.shared.message.GenerationTaskMessage
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OutboxGenerationTaskPublisher(private val outbox: OutboxRepository, private val mapper: ObjectMapper) : GenerationTaskPublisher {
    override fun enqueue(jobId: UUID, imageKey1: String, imageKey2: String) {
        outbox.insert("JOB", jobId, mapper.writeValueAsString(GenerationTaskMessage(jobId, imageKey1, imageKey2)))
    }
}
