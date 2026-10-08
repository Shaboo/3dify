package com.thridify.application.service.job.listuserjobs

import com.thridify.application.service.job.toResult
import com.thridify.domain.job.JobRepository
import org.springframework.stereotype.Service

@Service
class ListUserJobsApplicationService(private val jobs: JobRepository) {
    fun execute(query: ListUserJobsQuery) = jobs.findAllByUserId(query.userId, query.before).map { it.toResult() }
}
