package com.thridify.domain.shopify

import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ShopifyAccessPolicy {
    fun connected(store: ShopifyStore?): ShopifyStore {
        val found = store ?: throw ApiException(403, "Open the app in Shopify to connect your store")
        if (!found.connected) throw ApiException(403, "The Shopify app is disconnected")
        return found
    }
    fun generation(entitlement: ShopifyEntitlement, now: OffsetDateTime) {
        if (entitlement.status !in setOf("active", "trialing") || entitlement.periodEnd?.isAfter(now) != true) throw ApiException(402, "Choose an active Shopify plan before generating models")
        if (entitlement.generationsConsumed >= entitlement.generationLimit) throw ApiException(429, "This store has used its generation allowance")
    }
}
