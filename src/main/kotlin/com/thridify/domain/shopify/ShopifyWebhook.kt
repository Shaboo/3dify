package com.thridify.domain.shopify

import java.time.OffsetDateTime

data class ShopifyWebhook(val eventId: String, val topic: String, val shopId: String, val shopDomain: String, val occurredAt: OffsetDateTime)
