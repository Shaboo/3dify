package com.thridify.application.service.shopify.attach

import com.thridify.domain.shopify.ShopifyAttachmentException
import com.thridify.domain.shopify.ShopifyAttachmentRepository
import com.thridify.domain.shopify.ShopifyProductClient
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AttachShopifyModelsApplicationService(private val attachments: ShopifyAttachmentRepository, private val products: ShopifyProductClient, private val transactions: TransactionProvider, private val metrics: AppMetrics) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        val tasks = transactions.transaction { attachments.claimDue(5) }
        for (task in tasks) {
            var uploadStarted = false
            try {
                var media = products.findModel(task.store, task.productId, task.jobId)
                if (media == null && task.status in setOf("waiting", "retrying")) {
                    uploadStarted = transactions.transaction { attachments.beginUpload(task) }
                    if (!uploadStarted) continue
                    media = products.attachModel(task.store, task.productId, task.jobId, task.glbUrl)
                }
                val status = when {
                    media?.status == "READY" -> "attached"
                    media?.status == "FAILED" -> "failed"
                    media != null -> if (task.attempts >= 120) "failed" else "processing"
                    task.attempts >= 120 -> "failed"
                    else -> "checking"
                }
                val error = when {
                    media?.status == "FAILED" -> "Shopify could not process the model"
                    status == "failed" -> "Attachment needs review in Shopify; automatic creation is paused to prevent duplicate media"
                    status == "checking" -> "Checking whether Shopify accepted the model"
                    else -> null
                }
                transactions.transaction { attachments.finish(task, status, media?.id, error) }
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
                // Once productUpdate may have run, only check for the job marker; never blindly upload again.
                val ambiguous = uploadStarted && (ex !is ShopifyAttachmentException || ex.ambiguous)
                val status = when {
                    ambiguous -> "checking"
                    task.status == "checking" -> if (task.attempts >= 120) "failed" else "checking"
                    ex is ShopifyAttachmentException && !ex.retryable -> "failed"
                    task.attempts >= 10 -> "failed"
                    else -> "retrying"
                }
                transactions.transaction { attachments.finish(task, status, null, if (status == "failed") "Could not attach the model; reopen the app and check the product" else "Shopify attachment will be checked again") }
                metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_ATTACHMENT, if (status == "failed") AppMetrics.WorkflowOutcome.FAILED else AppMetrics.WorkflowOutcome.RETRY_SCHEDULED)
                log.warn("Shopify model attachment deferred jobId={} status={} error_type={}", task.jobId, status, ex.javaClass.simpleName)
            }
        }
    }
}
