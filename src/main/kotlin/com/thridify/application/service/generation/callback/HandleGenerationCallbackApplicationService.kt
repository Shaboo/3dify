package com.thridify.application.service.generation.callback

import com.thridify.domain.generation.CustomerWebhookClient
import com.thridify.domain.generation.GenerationOutcome
import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.JobNotification
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.webhook.WebhookRepository
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service

@Service
class HandleGenerationCallbackApplicationService(
    private val jobs: JobRepository,
    private val history: JobHistoryRepository,
    private val webhooks: WebhookRepository,
    private val client: CustomerWebhookClient,
    private val policy: GenerationPolicy,
    private val metrics: AppMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: HandleGenerationCallbackCommand): GenerationCallbackResult {
        val id = command.jobId
        MDC.put("jobId", id.toString())
        try {
            if (!policy.matchesTask(jobs.findByExternalTaskId(command.externalTaskId)?.id, id)) return GenerationCallbackResult.TASK_MISMATCH
            when (val outcome = policy.callbackOutcome(command.status, command.hasOutput, command.glbUrl, command.usdzUrl)) {
                is GenerationOutcome.Succeeded -> {
                    val job = jobs.findById(id)
                    jobs.markSuccess(id, outcome.glbUrl, outcome.usdzUrl)
                    history.insert(id, "SUCCESS", "GLB: ${outcome.glbUrl} | USDZ: ${outcome.usdzUrl}")
                    metrics.jobsCompleted.increment()
                    job?.let { metrics.recordJobDuration(System.currentTimeMillis() - it.createdAt.toInstant().toEpochMilli()) }
                    metrics.runpodCallbacks.increment()
                    log.info("Job {} completed via RunPod callback", id)
                    notifyCustomer(JobNotification(id, "SUCCESS", outcome.glbUrl, outcome.usdzUrl))
                }

                is GenerationOutcome.Failed -> {
                    val job = jobs.findById(id)
                    jobs.markFailed(id, outcome.message)
                    history.insert(id, "FAILED", outcome.message)
                    metrics.jobsFailed.increment()
                    job?.let { metrics.recordJobDuration(System.currentTimeMillis() - it.createdAt.toInstant().toEpochMilli()) }
                    metrics.runpodCallbacksFailed.increment()
                    log.warn("Job {} failed via RunPod callback", id)
                    notifyCustomer(JobNotification(id, "FAILED", null, null))
                }

                GenerationOutcome.InProgress -> log.info("Job {} in-progress state received [status={}]", id, command.status)
            }
            return GenerationCallbackResult.ACCEPTED
        } finally {
            MDC.remove("jobId")
        }
    }
    private fun notifyCustomer(notification: JobNotification) {
        try {
            val userId = jobs.findUserIdByJobId(notification.jobId) ?: return
            val url = webhooks.findByUserId(userId)?.url ?: return
            client.deliver(url, notification)
            metrics.webhookDeliveriesSuccess.increment()
            log.info("User webhook delivered [jobId={}, url={}]", notification.jobId, url)
        } catch (ex: Exception) {
            metrics.webhookDeliveriesFailed.increment()
            log.warn("User webhook delivery failed [jobId={}]: {}", notification.jobId, ex.message)
        }
    }
}
