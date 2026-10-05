package com.thridify.application.service.shopify.redact

import com.thridify.domain.shopify.ShopifyAssetDeletionClient
import com.thridify.domain.shopify.ShopifyWebhookRepository
import com.thridify.domain.transaction.TransactionProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RedactShopifyDataApplicationService(private val receipts: ShopifyWebhookRepository, private val deletion: ShopifyAssetDeletionClient, private val transactions: TransactionProvider) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (event in receipts.pendingRedactions(5)) {
            try {
                receipts.assetsForRedaction(event).forEach(deletion::deleteInput)
                receipts.outputsForRedaction(event).forEach(deletion::deleteOutput)
                transactions.transaction { receipts.completeRedaction(event) }
            } catch (ex: Exception) {
                log.warn("Shopify privacy request {} remains pending", event.eventId, ex)
            }
        }
    }
}
