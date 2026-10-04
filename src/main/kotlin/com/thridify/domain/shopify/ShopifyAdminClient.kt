package com.thridify.domain.shopify

/** Confirms the installed shop; merchandising belongs to the separate Shopify app. */
interface ShopifyAdminClient {
    fun shop(session: ShopifySession, token: String): ShopifyShop
}
