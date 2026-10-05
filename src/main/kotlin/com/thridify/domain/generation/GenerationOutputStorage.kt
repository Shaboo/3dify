package com.thridify.domain.generation

import java.util.UUID

interface GenerationOutputStorage {
    fun retain(jobId: UUID, provider: String, glbUrl: String, usdzUrl: String): RetainedGenerationOutputs
    fun delete(jobId: UUID)
}

data class RetainedGenerationOutputs(val glbUrl: String, val usdzUrl: String)
