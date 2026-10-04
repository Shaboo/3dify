package com.thridify.domain.shopify

import java.time.OffsetDateTime
import java.util.UUID

data class ShopifyStore(val connectionId: UUID, val workspaceId: UUID, val billingScopeId: UUID, val shopId: String, val shopDomain: String, val connected: Boolean, val installedAt: OffsetDateTime)
