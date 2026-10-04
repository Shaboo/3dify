package com.`3dify`.domain.job

import java.time.OffsetDateTime
import java.util.UUID

data class JobEntity(
    val id: UUID,
    val apiKeyId: UUID,
    val status: String,
    val externalTaskId: String?,
    val inputImage1: String,
    val inputImage2: String,
    val outputGlbUrl: String?,
    val outputUsdzUrl: String?,
    val webhookUrl: String?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
)
