package com.thridify.application.service.job.getjob

import com.thridify.application.service.job.toResult
import com.thridify.domain.job.JobRepository
import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class GetJobApplicationService(private val jobs: JobRepository) {
    fun execute(query: GetJobQuery) = (jobs.findById(query.jobId) ?: throw NotFoundException("Job not found: ${query.jobId}")).toResult()
}
