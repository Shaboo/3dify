package com.`3dify`.application.service.job.getjob

import com.`3dify`.application.service.job.toResult
import com.`3dify`.domain.job.JobRepository
import com.`3dify`.shared.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class GetJobApplicationService(private val jobs: JobRepository) {
    fun execute(query: GetJobQuery) = (jobs.findById(query.jobId) ?: throw NotFoundException("Job not found: ${query.jobId}")).toResult()
}
