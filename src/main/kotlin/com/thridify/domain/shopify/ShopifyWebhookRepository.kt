package com.thridify.domain.shopify

interface ShopifyWebhookRepository {
    fun accept(event: ShopifyWebhook): Boolean
    fun pendingRedactions(limit: Int): List<ShopifyWebhook>
    fun assetsForRedaction(event: ShopifyWebhook): List<String>
    fun outputsForRedaction(event: ShopifyWebhook): List<String>
    fun completeRedaction(event: ShopifyWebhook)
}
