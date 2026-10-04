package com.`3dify`.application.service.generation.dispatch

import com.`3dify`.domain.generation.GenerationProviderClient
import com.`3dify`.domain.job.JobHistoryRepository
import com.`3dify`.domain.job.JobRepository
import com.`3dify`.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service

@Service
class DispatchGenerationTaskApplicationService(
    private val jobs: JobRepository,
    private val history: JobHistoryRepository,
    private val provider: GenerationProviderClient,
    private val metrics: AppMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: DispatchGenerationTaskCommand) {
        val id = command.jobId
        MDC.put("jobId", id.toString())
        try {
            try {
                jobs.updateStatus(id, "PROCESSING")
                history.insert(id, "PROCESSING", "Worker picked up job")
                val taskId = provider.startGeneration(id, command.imageKey1, command.imageKey2)
                jobs.updateExternalTaskId(id, taskId)
                history.insert(id, "PROCESSING", "Job dispatched to GPU with ID: $taskId")
                metrics.jobsDispatched.increment()
                log.info("Job {} dispatched successfully to provider as task {}", id, taskId)
            } catch (ex: Exception) {
                log.error("Failed to dispatch job {} to provider: {}", id, ex.message, ex)
                val job = jobs.findById(id)
                // Keep the existing literal failure text until separately approved as a bug fix.
                val message = "GPU Provider Error: \${ex.message}"
                jobs.markFailed(id, message)
                history.insert(id, "FAILED", message)
                metrics.jobsFailed.increment()
                job?.let { metrics.recordJobDuration(System.currentTimeMillis() - it.createdAt.toInstant().toEpochMilli()) }
            }
        } finally {
            MDC.remove("jobId")
        }
    }
}
