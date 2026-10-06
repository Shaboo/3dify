package com.thridify.application.service.generation.reconcile

import com.thridify.domain.generation.CustomerWebhookClient
import com.thridify.domain.generation.GenerationOutputStorage
import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.generation.GenerationProviderResult
import com.thridify.domain.generation.GenerationProviderTask
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.generation.JobNotification
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.domain.webhook.WebhookRepository
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReconcileGenerationTasksApplicationService(
    private val tasks: GenerationProviderTaskRepository,
    private val providers: GenerationProviderRegistry,
    private val outputs: GenerationOutputStorage,
    private val jobs: JobRepository,
    private val history: JobHistoryRepository,
    private val transactions: TransactionProvider,
    private val webhooks: WebhookRepository,
    private val notifications: CustomerWebhookClient,
    private val metrics: AppMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (task in transactions.transaction { tasks.claimDue(10) }) {
            try {
                if (task.state == "retrying") {
                    retrySubmission(task)
                    continue
                }
                val result = providers.named(task.provider).retrieveTask(requireNotNull(task.taskId))
                if (result is GenerationProviderResult.Pending) {
                    metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_RECONCILIATION, AppMetrics.WorkflowOutcome.PENDING)
                    continue
                }
                val retained = if (result is GenerationProviderResult.Succeeded) outputs.retain(task.jobId, task.provider, result.glbUrl, result.usdzUrl) else null
                var removeOutputs = false
                var durationMs: Long? = null
                val notification = transactions.transaction {
                    val writable = tasks.canComplete(task.jobId)
                    val current = tasks.lock(task.jobId)
                    if (current == null || (!writable && current.state != "complete")) removeOutputs = retained != null
                    if (!writable || current?.state != "submitted" || current.leaseId != task.leaseId) {
                        null
                    } else {
                        val job = jobs.findById(task.jobId)
                        val status = if (retained != null) "SUCCESS" else "FAILED"
                        if (retained != null) {
                            jobs.markSuccess(task.jobId, retained.glbUrl, retained.usdzUrl)
                        } else {
                            jobs.markFailed(task.jobId, (result as GenerationProviderResult.Failed).message)
                        }
                        history.insert(task.jobId, status, if (retained != null) "Model outputs retained" else (result as GenerationProviderResult.Failed).message)
                        tasks.complete(task.jobId)
                        job?.let { durationMs = System.currentTimeMillis() - it.createdAt.toInstant().toEpochMilli() }
                        JobNotification(task.jobId, status, retained?.glbUrl, retained?.usdzUrl)
                    }
                }
                if (removeOutputs) outputs.delete(task.jobId)
                if (notification != null) {
                    metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_RECONCILIATION, AppMetrics.WorkflowOutcome.COMPLETED)
                    log.info("Generation reconciled jobId={} provider={} status={}", task.jobId, task.provider, notification.status)
                    durationMs?.let(metrics::recordJobDuration)
                    if (notification.status == "SUCCESS") metrics.jobsCompleted.increment() else metrics.jobsFailed.increment()
                    try {
                        jobs.findUserIdByJobId(task.jobId)?.let(webhooks::findByUserId)?.let {
                            notifications.deliver(it.url, notification)
                            metrics.webhookDeliveriesSuccess.increment()
                        }
                    } catch (ex: Exception) {
                        metrics.webhookDeliveriesFailed.increment()
                        log.warn("Customer generation notification remains undelivered for job {} error_type={}", task.jobId, ex.javaClass.simpleName)
                    }
                } else {
                    metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_RECONCILIATION, AppMetrics.WorkflowOutcome.IGNORED)
                }
            } catch (ex: Exception) {
                // Transient reads/downloads are safe to retry; never resubmit an upstream task.
                metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_RECONCILIATION, AppMetrics.WorkflowOutcome.FAILED)
                log.warn("Generation reconciliation remains pending for job {} error_type={}", task.jobId, ex.javaClass.simpleName)
            } finally {
                transactions.transaction { tasks.reschedule(task.jobId, requireNotNull(task.leaseId)) }
            }
        }
    }
    private fun retrySubmission(task: GenerationProviderTask) {
        val job = transactions.transaction {
            if (!tasks.canComplete(task.jobId) || !tasks.beginRetry(task.jobId, requireNotNull(task.leaseId))) null else jobs.findById(task.jobId)
        } ?: return
        val id = try {
            providers.named(task.provider).let { provider ->
                provider.validateInputImages(job.inputImages.size)
                if (job.inputImages.size == 2) {
                    provider.startGeneration(task.jobId, job.inputImages[0], job.inputImages[1])
                } else {
                    provider.startGeneration(task.jobId, job.inputImages)
                }
            }
        } catch (ex: Exception) {
            transactions.transaction {
                // Job-before-task lock order matches completion and privacy cleanup.
                jobs.lock(task.jobId)
                if (tasks.lock(task.jobId)?.leaseId == task.leaseId) {
                    if (ex is GenerationProviderException && !ex.ambiguous && ex.retryable) {
                        tasks.retrySubmission(task.jobId, ex.retryAfterSeconds)
                    } else {
                        tasks.submissionFailed(task.jobId, (ex as? GenerationProviderException)?.ambiguous != false)
                        jobs.markFailed(task.jobId, "Generation provider could not accept the task")
                        history.insert(task.jobId, "FAILED", "Generation provider could not accept the task")
                    }
                }
            }
            val retryable = ex is GenerationProviderException && !ex.ambiguous && ex.retryable
            val uncertain = (ex as? GenerationProviderException)?.ambiguous != false
            metrics.recordWorkflow(
                AppMetrics.Workflow.GENERATION_RECONCILIATION,
                if (retryable) {
                    AppMetrics.WorkflowOutcome.RETRY_SCHEDULED
                } else if (uncertain) {
                    AppMetrics.WorkflowOutcome.UNCERTAIN
                } else {
                    AppMetrics.WorkflowOutcome.FAILED
                },
            )
            log.warn("Generation resubmission deferred or failed jobId={} provider={} retryable={} uncertain={} error_type={}", task.jobId, task.provider, retryable, uncertain, ex.javaClass.simpleName)
            return
        }
        try {
            transactions.transaction {
                jobs.updateExternalTaskId(task.jobId, "${task.provider}:$id")
                tasks.acknowledge(task.jobId, id)
                history.insert(task.jobId, "PROCESSING", "Job dispatched to generation provider")
            }
            metrics.jobsDispatched.increment()
            metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_RECONCILIATION, AppMetrics.WorkflowOutcome.COMPLETED)
        } catch (ex: Exception) {
            log.error("Provider {} acknowledged task {} for job {}; submission requires reconciliation", task.provider, id, task.jobId)
            throw ex
        }
    }
}
