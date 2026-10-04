package com.`3dify`.domain.job

import com.`3dify`.domain.job.JobHistoryEntity
import java.util.UUID

interface JobHistoryRepository {
    fun insert(jobId: UUID, status: String, details: String?): Unit
    fun findAllByJobId(jobId: UUID): List<JobHistoryEntity>
}
