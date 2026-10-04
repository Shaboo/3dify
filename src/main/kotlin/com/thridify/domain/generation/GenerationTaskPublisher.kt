package com.thridify.domain.generation

import java.util.UUID

interface GenerationTaskPublisher {
    fun publish(jobId: UUID, imageKey1: String, imageKey2: String)
}
