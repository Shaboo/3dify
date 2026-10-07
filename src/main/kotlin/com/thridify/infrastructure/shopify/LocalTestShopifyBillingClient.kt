package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyBillingSnapshot
import com.thridify.shared.exception.ApiException
import java.time.Clock
import java.time.OffsetDateTime

open class LocalTestShopifyBillingClient(private val config: ShopifyProperties, private val clock: Clock = Clock.systemUTC()) : ShopifyBillingClient {
    init {
        require(Regex("gid://shopify/Shop/[0-9]+").matches(config.localTestShopId)) { "Configure shopify.local-test-shop-id with the verified test store ID" }
        require(Regex("[a-z0-9][a-z0-9-]*[.]myshopify[.]com").matches(config.localTestShopDomain)) { "Configure shopify.local-test-shop-domain with the test store domain" }
        require(config.localTestGenerationLimit > 0) { "Shopify local test generation limit must be positive" }
    }

    override fun currentSubscription(shopId: String): ShopifyBillingSnapshot {
        config.requireEnabled()
        if (shopId != config.localTestShopId) throw ApiException(403, "Local testing is restricted to the configured Shopify test store")
        val start = OffsetDateTime.now(clock).withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
        return ShopifyBillingSnapshot(emptySet(), "monthly", "active", start, start.plusMonths(1), null, config.localTestGenerationLimit)
    }

    override fun pricingUrl(shopDomain: String): String {
        if (shopDomain != config.localTestShopDomain) throw ApiException(403, "Local testing is restricted to the configured Shopify test store")
        return ""
    }
}
