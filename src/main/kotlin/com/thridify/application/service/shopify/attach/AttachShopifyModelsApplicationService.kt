package com.thridify.application.service.shopify.attach

import com.thridify.domain.shopify.ShopifyAttachmentPolicy
import com.thridify.domain.shopify.ShopifyAttachmentRepository
import com.thridify.domain.shopify.ShopifyProductClient
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AttachShopifyModelsApplicationService(private val attachments: ShopifyAttachmentRepository, private val products: ShopifyProductClient, private val transactions: TransactionProvider, private val metrics: AppMetrics, private val policy: ShopifyAttachmentPolicy) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        val tasks = transactions.transaction { attachments.claimDue(5) }
        for (task in tasks) {
            var uploadStarted = false
            try {
                var media = products.findModel(task.store, task.productId, task.jobId)
                if (media == null && policy.mayUpload(task.status)) {
                    uploadStarted = transactions.transaction { attachments.beginUpload(task) }
                    if (!uploadStarted) continue
                    media = products.attachModel(task.store, task.productId, task.jobId, task.glbUrl)
                }
                val decision = policy.observed(media, task.attempts)
                val status = decision.status
                transactions.transaction { attachments.finish(task, status, media?.id, decision.error) }
                metrics.recordWorkflow(
                    AppMetrics.Workflow.SHOPIFY_ATTACHMENT,
                    if (status == "attached") {
                        AppMetrics.WorkflowOutcome.COMPLETED
                    } else if (status == "failed") {
                        AppMetrics.WorkflowOutcome.FAILED
                    } else {
                        AppMetrics.WorkflowOutcome.PENDING
                    },
                )
                log.info("Shopify model attachment jobId={} status={}", task.jobId, status)
            } catch (ex: Exception) {
                val decision = policy.failed(task.status, task.attempts, uploadStarted, ex)
                val status = decision.status
                transactions.transaction { attachments.finish(task, status, null, decision.error) }
                metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_ATTACHMENT, if (status == "failed") AppMetrics.WorkflowOutcome.FAILED else AppMetrics.WorkflowOutcome.RETRY_SCHEDULED)
                log.warn("Shopify model attachment deferred jobId={} status={} error_type={}", task.jobId, status, ex.javaClass.simpleName)
            }
        }
    }
}
