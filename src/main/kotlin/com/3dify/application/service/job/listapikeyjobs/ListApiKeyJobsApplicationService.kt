package com.`3dify`.application.service.job.listapikeyjobs

import com.`3dify`.application.service.job.toResult
import com.`3dify`.domain.job.JobRepository
import org.springframework.stereotype.Service

@Service
class ListApiKeyJobsApplicationService(private val jobs: JobRepository) {
    fun execute(query: ListApiKeyJobsQuery) = jobs.findAllByApiKeyId(query.apiKeyId).map { it.toResult() }
}
