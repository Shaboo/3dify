package com.`3dify`.application.service.job.history

import java.util.UUID

data class JobHistoryResult(
    val id: UUID,
    val jobId: UUID,
    val status: String,
    val details: String?,
    val createdAt: String,
)
