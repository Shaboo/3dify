package com.thridify.domain.job

import com.thridify.domain.job.JobHistoryEntity
import java.util.UUID

interface JobHistoryRepository {
    fun insert(jobId: UUID, status: String, details: String?): Unit
    fun findAllByJobId(jobId: UUID): List<JobHistoryEntity>
}
