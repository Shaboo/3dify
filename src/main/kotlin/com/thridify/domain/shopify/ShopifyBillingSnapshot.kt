package com.thridify.domain.shopify

import java.time.OffsetDateTime

data class ShopifyBillingSnapshot(val offerHandles: Set<String>, val interval: String, val status: String, val periodStart: OffsetDateTime?, val periodEnd: OffsetDateTime, val externalSubscriptionId: String?, val localTestGenerationLimit: Int? = null)
