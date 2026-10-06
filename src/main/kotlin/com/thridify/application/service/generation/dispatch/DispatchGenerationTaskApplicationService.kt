package com.thridify.application.service.generation.dispatch

import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DispatchGenerationTaskApplicationService(
    private val jobs: JobRepository,
    private val history: JobHistoryRepository,
    private val providers: GenerationProviderRegistry,
    private val tasks: GenerationProviderTaskRepository,
    private val transactions: TransactionProvider,
    private val metrics: AppMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: DispatchGenerationTaskCommand) {
        val id = command.jobId
        val provider = providers.current()
        val reserved = transactions.transaction {
            if (!tasks.reserve(id, provider.name)) {
                false
            } else {
                jobs.updateStatus(id, "PROCESSING")
                history.insert(id, "PROCESSING", "Worker picked up job")
                true
            }
        }
        if (!reserved) {
            metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_DISPATCH, AppMetrics.WorkflowOutcome.IGNORED)
            log.debug("Generation dispatch skipped jobId={} reason=already_reserved", id)
            return
        }
        val taskId = try {
            // External mutation happens after reservation commits, never in a DB transaction.
            provider.startGeneration(id, command.imageKey1, command.imageKey2)
        } catch (ex: Exception) {
            if (ex is GenerationProviderException && !ex.ambiguous && ex.retryable) {
                transactions.transaction { tasks.retrySubmission(id, ex.retryAfterSeconds) }
                metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_DISPATCH, AppMetrics.WorkflowOutcome.RETRY_SCHEDULED)
                log.warn("Generation dispatch deferred jobId={} provider={} retry_after_seconds={}", id, provider.name, ex.retryAfterSeconds)
                return
            }
            transactions.transaction {
                tasks.submissionFailed(id, (ex as? GenerationProviderException)?.ambiguous != false)
                jobs.markFailed(id, "Generation provider could not accept the task")
                history.insert(id, "FAILED", "Generation provider could not accept the task")
            }
            metrics.jobsFailed.increment()
            val uncertain = (ex as? GenerationProviderException)?.ambiguous != false
            metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_DISPATCH, if (uncertain) AppMetrics.WorkflowOutcome.UNCERTAIN else AppMetrics.WorkflowOutcome.FAILED)
            log.error("Generation dispatch failed jobId={} provider={} uncertain={} error_type={}", id, provider.name, uncertain, ex.javaClass.simpleName)
            return
        }
        // A DB failure here leaves a submitting reservation for manual reconciliation.
        // Re-delivery cannot issue another charged upstream POST.
        try {
            transactions.transaction {
                jobs.updateExternalTaskId(id, "${provider.name}:$taskId")
                tasks.acknowledge(id, taskId)
                history.insert(id, "PROCESSING", "Job dispatched to generation provider")
            }
        } catch (ex: Exception) {
            log.error("Provider {} acknowledged task {} for job {}; submission requires reconciliation", provider.name, taskId, id)
            throw ex
        }
        metrics.jobsDispatched.increment()
        metrics.recordWorkflow(AppMetrics.Workflow.GENERATION_DISPATCH, AppMetrics.WorkflowOutcome.COMPLETED)
        log.info("Generation dispatched jobId={} provider={}", id, provider.name)
    }
}
