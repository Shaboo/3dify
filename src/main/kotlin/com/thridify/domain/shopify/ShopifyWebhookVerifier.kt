package com.thridify.domain.shopify

interface ShopifyWebhookVerifier {
    fun verify(body: ByteArray, signature: String, topic: String, eventId: String, shopDomain: String, triggeredAt: String?): ShopifyWebhook
}
