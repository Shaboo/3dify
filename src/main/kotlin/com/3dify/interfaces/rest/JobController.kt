package com.`3dify`.interfaces.rest

import com.`3dify`.application.service.job.history.GetJobHistoryApplicationService
import com.`3dify`.application.service.job.history.GetJobHistoryQuery
import com.`3dify`.application.service.job.listuserjobs.ListUserJobsApplicationService
import com.`3dify`.application.service.job.listuserjobs.ListUserJobsQuery
import com.`3dify`.interfaces.rest.dto.toResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/dashboard/jobs")
class JobController(private val listJobs: ListUserJobsApplicationService, private val history: GetJobHistoryApplicationService) {
    @GetMapping
    fun listJobs(authentication: Authentication) = listJobs.execute(ListUserJobsQuery(UUID.fromString(authentication.principal as String))).map { it.toResponse() }

    @GetMapping("/{jobId}/history")
    fun getJobHistory(@PathVariable jobId: UUID) = history.execute(GetJobHistoryQuery(jobId)).map { it.toResponse() }
}
