package com.thridify.domain.generation

import java.util.UUID

data class GenerationProviderTask(val jobId: UUID, val provider: String, val taskId: String?, val state: String, val leaseId: UUID? = null) {
    init {
        require(Regex("[a-z][a-z0-9_-]{0,49}").matches(provider)) { "Invalid generation provider name" }
        require(state in setOf("submitting", "submitted", "retrying", "uncertain", "failed", "complete")) { "Invalid generation submission state" }
        require(state != "submitted" || !taskId.isNullOrBlank()) { "Submitted generation requires a provider task" }
    }
}
