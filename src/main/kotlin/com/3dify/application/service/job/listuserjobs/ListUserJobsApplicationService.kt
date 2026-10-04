package com.`3dify`.application.service.job.listuserjobs

import com.`3dify`.application.service.job.toResult
import com.`3dify`.domain.job.JobRepository
import org.springframework.stereotype.Service

@Service
class ListUserJobsApplicationService(private val jobs: JobRepository) {
    fun execute(query: ListUserJobsQuery) = jobs.findAllByUserId(query.userId).map { it.toResult() }
}
