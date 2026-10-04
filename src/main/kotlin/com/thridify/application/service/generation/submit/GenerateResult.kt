package com.thridify.application.service.generation.submit

import java.util.UUID

data class GenerateResult(
    val jobId: UUID,
    val status: String,
)
