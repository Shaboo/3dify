package com.thridify.application.service.generation.callback

import com.thridify.domain.generation.CustomerWebhookPublisher
import com.thridify.domain.generation.GenerationOutcome
import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.generation.JobNotification
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
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
    private val client: CustomerWebhookPublisher,
    private val policy: GenerationPolicy,
    private val metrics: AppMetrics,
    private val tasks: GenerationProviderTaskRepository,
    private val transactions: TransactionProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: HandleGenerationCallbackCommand): GenerationCallbackResult {
        val id = command.jobId
        val previousJobId = MDC.get("jobId")
        MDC.put("jobId", id.toString())
        try {
            var notification: JobNotification? = null
            var completedJob: com.thridify.domain.job.JobEntity? = null
            val accepted = transactions.transaction {
                val writable = tasks.canComplete(id)
                val job = jobs.lock(id) ?: return@transaction false
                val binding = tasks.lock(id)
                if (binding != null && binding.provider != "runpod") return@transaction false
                if (job.externalTaskId !in setOf(command.externalTaskId, "runpod:${command.externalTaskId}")) return@transaction false
                if (!writable) return@transaction true
                when (val outcome = policy.callbackOutcome(command.status, command.hasOutput, command.glbUrl, command.usdzUrl)) {
                    is GenerationOutcome.Succeeded -> {
                        jobs.markSuccess(id, outcome.glbUrl, outcome.usdzUrl)
                        history.insert(id, "SUCCESS", "GLB: ${outcome.glbUrl} | USDZ: ${outcome.usdzUrl}")
                        tasks.complete(id)
                        completedJob = job
                        notification = JobNotification(id, "SUCCESS", outcome.glbUrl, outcome.usdzUrl)
                    }

                    is GenerationOutcome.Failed -> {
                        jobs.markFailed(id, outcome.message)
                        history.insert(id, "FAILED", outcome.message)
                        tasks.complete(id)
                        completedJob = job
                        notification = JobNotification(id, "FAILED", null, null)
                    }

                    GenerationOutcome.InProgress -> Unit
                }
                notification?.let { notifyCustomer(it) }
                true
            }
            if (!accepted) {
                metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_CALLBACK, AppMetrics.WorkflowOutcome.IGNORED)
                log.warn("Generation callback rejected jobId={} reason=task_mismatch", id)
                return GenerationCallbackResult.TASK_MISMATCH
            }
            notification?.let {
                log.info("Generation completed jobId={} status={}", id, it.status)
                if (it.status == "SUCCESS") {
                    metrics.jobsCompleted.increment()
                    metrics.runpodCallbacks.increment()
                } else {
                    metrics.jobsFailed.increment()
                    metrics.runpodCallbacksFailed.increment()
                }
                completedJob?.let { job -> metrics.recordJobDuration(System.currentTimeMillis() - job.createdAt.toInstant().toEpochMilli()) }
            }
            metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_CALLBACK, AppMetrics.WorkflowOutcome.ACCEPTED)
            return GenerationCallbackResult.ACCEPTED
        } finally {
            if (previousJobId == null) MDC.remove("jobId") else MDC.put("jobId", previousJobId)
        }
    }
    private fun notifyCustomer(notification: JobNotification) {
        val userId = jobs.findUserIdByJobId(notification.jobId) ?: return
        val url = webhooks.findByUserId(userId)?.url ?: return
        client.publish(url, notification)
    }
}
