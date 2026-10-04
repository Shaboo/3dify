package com.thridify.application.service.shopify.webhook

import com.thridify.domain.shopify.ShopifyWebhookRepository
import com.thridify.domain.shopify.ShopifyWebhookVerifier
import com.thridify.domain.transaction.TransactionProvider
import org.springframework.stereotype.Service

@Service
class HandleShopifyWebhookApplicationService(private val verifier: ShopifyWebhookVerifier, private val receipts: ShopifyWebhookRepository, private val transactions: TransactionProvider) {
    fun execute(command: HandleShopifyWebhookCommand) {
        val event = verifier.verify(command.body, command.signature, command.topic, command.eventId, command.shopDomain, command.triggeredAt)
        transactions.transaction { receipts.accept(event) }
    }
}

data class HandleShopifyWebhookCommand(val body: ByteArray, val signature: String, val topic: String, val eventId: String, val shopDomain: String, val triggeredAt: String?)
