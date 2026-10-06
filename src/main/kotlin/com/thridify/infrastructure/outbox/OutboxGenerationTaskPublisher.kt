package com.thridify.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.generation.GenerationTaskPublisher
import com.thridify.shared.message.GenerationTaskMessage
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OutboxGenerationTaskPublisher(private val outbox: OutboxRepository, private val mapper: ObjectMapper) : GenerationTaskPublisher {
    override fun publish(jobId: UUID, imageKeys: List<String>) {
        if (imageKeys.size == 2) return publish(jobId, imageKeys[0], imageKeys[1])
        outbox.insert("JOB", jobId, mapper.writeValueAsString(GenerationTaskMessage(jobId, imageKeys.first(), imageKeys.getOrElse(1) { imageKeys.first() }, imageKeys)))
    }
    override fun publish(jobId: UUID, imageKey1: String, imageKey2: String) {
        outbox.insert("JOB", jobId, mapper.writeValueAsString(GenerationTaskMessage(jobId, imageKey1, imageKey2)))
    }
}
