package com.thridify.application.service.shopify.redact

import com.thridify.domain.generation.GenerationOutputStorage
import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.shopify.ShopifyAssetDeletionClient
import com.thridify.domain.shopify.ShopifyWebhookRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RedactShopifyDataApplicationService(private val receipts: ShopifyWebhookRepository, private val deletion: ShopifyAssetDeletionClient, private val transactions: TransactionProvider, private val tasks: GenerationProviderTaskRepository, private val providers: GenerationProviderRegistry, private val outputs: GenerationOutputStorage, private val metrics: AppMetrics) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (event in receipts.pendingRedactions(5)) {
            try {
                for (task in tasks.forRedaction(event)) {
                    check(task.state !in setOf("submitting", "uncertain") && !(task.state == "retrying" && task.leaseId != null)) { "Provider submission needs reconciliation before privacy cleanup" }
                    task.taskId?.let { providers.named(task.provider).deleteTask(it) }
                    outputs.delete(task.jobId)
                }
                receipts.assetsForRedaction(event).forEach(deletion::deleteInput)
                receipts.outputsForRedaction(event).forEach(deletion::deleteOutput)
                transactions.transaction { receipts.completeRedaction(event) }
                metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_PRIVACY, AppMetrics.WorkflowOutcome.COMPLETED)
                log.info("Shopify privacy request completed eventId={}", event.eventId)
            } catch (ex: Exception) {
                metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_PRIVACY, AppMetrics.WorkflowOutcome.FAILED)
                log.warn("Shopify privacy request {} remains pending error_type={}", event.eventId, ex.javaClass.simpleName)
            }
        }
    }
}
