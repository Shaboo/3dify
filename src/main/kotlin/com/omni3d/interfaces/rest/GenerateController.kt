package com.omni3d.interfaces.rest

import com.omni3d.shared.exception.BadRequestException
import com.omni3d.interfaces.rest.dto.GenerateResponse
import com.omni3d.interfaces.rest.dto.JobHistoryEntry
import com.omni3d.interfaces.rest.dto.JobResponse
import com.omni3d.application.service.JobHistoryService
import com.omni3d.application.service.JobService
import com.omni3d.infrastructure.storage.StorageService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/api/v1")
class GenerateController(
    private val jobService: JobService,
    private val storageService: StorageService,
    private val jobHistoryService: JobHistoryService
) {

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun generate(
        authentication: Authentication,
        @RequestPart("image1") image1: MultipartFile,
        @RequestPart("image2") image2: MultipartFile
    ): GenerateResponse {
        if (image1.isEmpty || image2.isEmpty) {
            throw BadRequestException("Both image1 and image2 are required")
        }

        val apiKeyId = UUID.fromString(authentication.principal as String)

        // Upload input images to R2
        val key1 = "inputs/${UUID.randomUUID()}_${image1.originalFilename}"
        val key2 = "inputs/${UUID.randomUUID()}_${image2.originalFilename}"

        storageService.upload(key1, image1.bytes, image1.contentType ?: "image/png")
        storageService.upload(key2, image2.bytes, image2.contentType ?: "image/png")

        // Create job record + outbox event (transactional)
        val jobId = jobService.createJob(apiKeyId, key1, key2)

        return GenerateResponse(jobId = jobId, status = "PENDING")
    }

    @GetMapping("/jobs")
    fun listJobs(authentication: Authentication): List<JobResponse> {
        val apiKeyId = UUID.fromString(authentication.principal as String)
        return jobService.listJobsByApiKey(apiKeyId)
    }

    @GetMapping("/jobs/{jobId}")
    fun getJob(@PathVariable jobId: UUID): JobResponse =
        jobService.getJobById(jobId)

    @GetMapping("/jobs/{jobId}/history")
    fun getJobHistory(@PathVariable jobId: UUID): List<JobHistoryEntry> =
        jobHistoryService.getHistory(jobId)
}
