package com.thridify.application.service.shopify.subscription

import com.thridify.domain.shopify.ShopifyAccessPolicy
import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.domain.transaction.TransactionProvider
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class GetShopifySubscriptionApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val billing: ShopifyBillingClient, private val access: ShopifyAccessPolicy, private val transactions: TransactionProvider) {
    fun execute(query: GetShopifySubscriptionQuery): ShopifySubscriptionResult {
        val store = access.connected(stores.findByDomain(sessions.verify(query.idToken).shopDomain))
        val observedAt = OffsetDateTime.now()
        val snapshot = billing.currentSubscription(store.shopId)
        val entitlement = transactions.transaction { stores.synchronize(store, snapshot, observedAt) }
        return ShopifySubscriptionResult(entitlement.status, entitlement.planName, entitlement.periodEnd?.toString(), entitlement.generationLimit, entitlement.generationsConsumed, billing.pricingUrl(store.shopDomain), entitlement.allowancePeriodStart?.toString(), entitlement.allowancePeriodEnd?.toString(), snapshot?.localTestGenerationLimit != null)
    }
}

data class GetShopifySubscriptionQuery(val idToken: String)
data class ShopifySubscriptionResult(val status: String, val planName: String?, val periodEnd: String?, val generationLimit: Int, val generationsConsumed: Int, val pricingUrl: String, val allowancePeriodStart: String?, val allowancePeriodEnd: String?, val localTesting: Boolean = false)
