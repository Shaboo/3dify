package com.`3dify`.application.service.job.history

import com.`3dify`.application.service.job.history.JobHistoryResult
import com.`3dify`.domain.job.JobHistoryRepository
import org.springframework.stereotype.Service

@Service
class GetJobHistoryApplicationService(private val history: JobHistoryRepository) {
    fun execute(query: GetJobHistoryQuery) = history.findAllByJobId(query.jobId).map {
        JobHistoryResult(it.id, it.jobId, it.status, it.details, it.createdAt.toString())
    }
}
