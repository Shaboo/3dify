package com.omni3d.api.controller

import com.omni3d.api.model.dto.JobHistoryEntry
import com.omni3d.api.model.dto.JobResponse
import com.omni3d.api.service.JobHistoryService
import com.omni3d.api.service.JobService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/dashboard/jobs")
class JobController(
    private val jobService: JobService,
    private val jobHistoryService: JobHistoryService
) {

    @GetMapping
    fun listJobs(authentication: Authentication): List<JobResponse> {
        val userId = UUID.fromString(authentication.principal as String)
        return jobService.listJobsByUser(userId)
    }

    @GetMapping("/{jobId}/history")
    fun getJobHistory(@PathVariable jobId: UUID): List<JobHistoryEntry> =
        jobHistoryService.getHistory(jobId)
}
