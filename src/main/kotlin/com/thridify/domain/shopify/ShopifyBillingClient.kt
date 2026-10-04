package com.thridify.domain.shopify

interface ShopifyBillingClient {
    fun currentSubscription(shopId: String): ShopifyBillingSnapshot?
    fun pricingUrl(shopDomain: String): String
}
