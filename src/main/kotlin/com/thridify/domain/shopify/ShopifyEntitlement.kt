package com.thridify.domain.shopify

import java.time.OffsetDateTime
import java.util.UUID

data class ShopifyEntitlement(val planId: UUID?, val planName: String?, val status: String, val periodStart: OffsetDateTime?, val periodEnd: OffsetDateTime?, val generationLimit: Int, val generationsConsumed: Int)
