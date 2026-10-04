package com.`3dify`.domain.generation

import java.util.UUID

interface GenerationTaskPublisher {
    fun enqueue(jobId: UUID, imageKey1: String, imageKey2: String)
}
