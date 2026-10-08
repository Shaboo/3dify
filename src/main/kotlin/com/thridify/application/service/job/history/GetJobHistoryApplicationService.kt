package com.thridify.application.service.job.history

import com.thridify.application.service.job.history.JobHistoryResult
import com.thridify.domain.job.JobHistoryRepository
import org.springframework.stereotype.Service

@Service
class GetJobHistoryApplicationService(private val history: JobHistoryRepository, private val jobs: com.thridify.domain.job.JobRepository) {
    fun execute(query: GetJobHistoryQuery): List<JobHistoryResult> {
        val job = if (query.apiKey) jobs.findByApiKey(query.jobId, query.callerId) else jobs.findByUser(query.jobId, query.callerId)
        if (job == null) throw com.thridify.shared.exception.NotFoundException("Job not found")
        return history.findAllByJobId(query.jobId).map {
            JobHistoryResult(it.id, it.jobId, it.status, it.details, it.createdAt.toString())
        }
    }
}
