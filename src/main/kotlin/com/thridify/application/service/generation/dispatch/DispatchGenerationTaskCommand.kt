package com.thridify.application.service.generation.dispatch

import java.util.UUID

data class DispatchGenerationTaskCommand(val jobId: UUID, val imageKeys: List<String>) {
    constructor(jobId: UUID, imageKey1: String, imageKey2: String) : this(jobId, listOf(imageKey1, imageKey2))
}
