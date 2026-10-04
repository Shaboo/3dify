package com.`3dify`.domain.job

import java.time.OffsetDateTime
import java.util.UUID

data class JobHistoryEntity(
    val id: UUID,
    val jobId: UUID,
    val status: String,
    val details: String?,
    val createdAt: OffsetDateTime,
)
