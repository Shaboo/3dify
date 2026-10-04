package com.thridify.application.service.job.listapikeyjobs

import com.thridify.application.service.job.toResult
import com.thridify.domain.job.JobRepository
import org.springframework.stereotype.Service

@Service
class ListApiKeyJobsApplicationService(private val jobs: JobRepository) {
    fun execute(query: ListApiKeyJobsQuery) = jobs.findAllByApiKeyId(query.apiKeyId).map { it.toResult() }
}
