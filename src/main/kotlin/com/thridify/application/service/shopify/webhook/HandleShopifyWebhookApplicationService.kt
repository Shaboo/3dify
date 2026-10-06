package com.thridify.application.service.shopify.webhook

import com.thridify.domain.shopify.ShopifyWebhookRepository
import com.thridify.domain.shopify.ShopifyWebhookVerifier
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HandleShopifyWebhookApplicationService(private val verifier: ShopifyWebhookVerifier, private val receipts: ShopifyWebhookRepository, private val transactions: TransactionProvider, private val metrics: AppMetrics) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: HandleShopifyWebhookCommand) {
        val event = verifier.verify(command.body, command.signature, command.topic, command.eventId, command.shopDomain, command.triggeredAt)
        val accepted = transactions.transaction { receipts.accept(event) }
        metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_WEBHOOK, if (accepted) AppMetrics.WorkflowOutcome.ACCEPTED else AppMetrics.WorkflowOutcome.IGNORED)
        log.info("Shopify webhook processed accepted={}", accepted)
    }
}

data class HandleShopifyWebhookCommand(val body: ByteArray, val signature: String, val topic: String, val eventId: String, val shopDomain: String, val triggeredAt: String?)
