package com.thridify.domain.generation

import java.util.UUID

interface GenerationTaskPublisher {
    fun publish(jobId: UUID, imageKeys: List<String>) {
        require(imageKeys.size == 2) { "This publisher does not support image lists" }
        publish(jobId, imageKeys[0], imageKeys[1])
    }
    fun publish(jobId: UUID, imageKey1: String, imageKey2: String)
}
