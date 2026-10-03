package com.omni3d.api.service

import com.omni3d.api.domain.JobEntity
import com.omni3d.api.exception.NotFoundException
import com.omni3d.api.messaging.TaskProducer
import com.omni3d.api.metrics.AppMetrics
import com.omni3d.api.model.dto.JobResponse
import com.omni3d.api.repository.JobRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class JobService(
    private val jobRepository: JobRepository,
    private val jobHistoryService: JobHistoryService,
    private val outboxService: OutboxService,
    private val metrics: AppMetrics
) {
    private val log = LoggerFactory.getLogger(JobService::class.java)

    @Transactional
    fun createJob(apiKeyId: UUID, imageKey1: String, imageKey2: String): UUID {
        val jobId = UUID.randomUUID()
        MDC.put("jobId", jobId.toString())
        try {
            log.info("Creating new job [apiKeyId={}]", apiKeyId)
            jobRepository.insert(jobId, apiKeyId, imageKey1, imageKey2)
            jobHistoryService.recordChange(jobId, "PENDING", "Job created")

            val taskMessage = TaskProducer.TaskMessage(
                jobId          = jobId,
                inputImage1Key = imageKey1,
                inputImage2Key = imageKey2
            )
            outboxService.saveEvent("JOB", jobId, taskMessage)
            metrics.jobsCreated.increment()
            log.info("Job {} created and queued in outbox", jobId)
            return jobId
        } finally {
            MDC.remove("jobId")
        }
    }

    fun markProcessing(jobId: UUID) {
        MDC.put("jobId", jobId.toString())
        try {
            log.info("Marking job {} as PROCESSING", jobId)
            jobRepository.updateStatus(jobId, "PROCESSING")
            jobHistoryService.recordChange(jobId, "PROCESSING", "Worker picked up job")
        } finally {
            MDC.remove("jobId")
        }
    }

    fun markSuccess(jobId: UUID, glbUrl: String, usdzUrl: String) {
        MDC.put("jobId", jobId.toString())
        try {
            log.info("Marking job {} as SUCCESS [glb={}, usdz={}]", jobId, glbUrl, usdzUrl)
            val job = jobRepository.findById(jobId)
            jobRepository.markSuccess(jobId, glbUrl, usdzUrl)
            jobHistoryService.recordChange(jobId, "SUCCESS", "GLB: $glbUrl | USDZ: $usdzUrl")
            metrics.jobsCompleted.increment()

            // Record duration if we have the creation timestamp
            if (job != null) {
                val durationMs = System.currentTimeMillis() - job.createdAt.toInstant().toEpochMilli()
                metrics.recordJobDuration(durationMs)
                log.info("Job {} completed in {}ms", jobId, durationMs)
            }
        } finally {
            MDC.remove("jobId")
        }
    }

    fun markFailed(jobId: UUID, errorMessage: String) {
        MDC.put("jobId", jobId.toString())
        try {
            log.warn("Marking job {} as FAILED: {}", jobId, errorMessage)
            val job = jobRepository.findById(jobId)
            jobRepository.markFailed(jobId, errorMessage)
            jobHistoryService.recordChange(jobId, "FAILED", errorMessage)
            metrics.jobsFailed.increment()

            if (job != null) {
                val durationMs = System.currentTimeMillis() - job.createdAt.toInstant().toEpochMilli()
                metrics.recordJobDuration(durationMs)
            }
        } finally {
            MDC.remove("jobId")
        }
    }

    fun getJobById(jobId: UUID): JobResponse {
        val job = jobRepository.findById(jobId)
            ?: throw NotFoundException("Job not found: $jobId")
        return job.toResponse()
    }

    fun updateExternalTaskId(jobId: UUID, externalTaskId: String) {
        MDC.put("jobId", jobId.toString())
        try {
            log.info("Job {} bound to provider task {}", jobId, externalTaskId)
            jobRepository.updateExternalTaskId(jobId, externalTaskId)
            jobHistoryService.recordChange(jobId, "PROCESSING", "Job dispatched to GPU with ID: $externalTaskId")
            metrics.jobsDispatched.increment()
        } finally {
            MDC.remove("jobId")
        }
    }

    fun findJobByExternalTaskId(externalTaskId: String): UUID? =
        jobRepository.findByExternalTaskId(externalTaskId)?.id

    fun getUserIdForJob(jobId: UUID): UUID? =
        jobRepository.findUserIdByJobId(jobId)

    fun listJobsByApiKey(apiKeyId: UUID): List<JobResponse> =
        jobRepository.findAllByApiKeyId(apiKeyId).map { it.toResponse() }

    fun listJobsByUser(userId: UUID): List<JobResponse> =
        jobRepository.findAllByUserId(userId).map { it.toResponse() }
}

private fun JobEntity.toResponse() = JobResponse(
    id            = id,
    status        = status,
    inputImage1   = inputImage1,
    inputImage2   = inputImage2,
    outputGlbUrl  = outputGlbUrl,
    outputUsdzUrl = outputUsdzUrl,
    errorMessage  = errorMessage,
    createdAt     = createdAt.toString(),
    completedAt   = completedAt?.toString()
)
