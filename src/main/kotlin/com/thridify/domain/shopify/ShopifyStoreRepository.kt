package com.thridify.domain.shopify

import java.time.OffsetDateTime

interface ShopifyStoreRepository {
    fun connect(shop: ShopifyShop): ShopifyStore
    fun findByDomain(domain: String): ShopifyStore?
    fun dueForReconciliation(limit: Int): List<ShopifyStore>
    fun synchronize(store: ShopifyStore, snapshot: ShopifyBillingSnapshot?, observedAt: OffsetDateTime): ShopifyEntitlement
}
