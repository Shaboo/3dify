package com.thridify.domain.shopify

/** Confirms the installed shop. Product operations use ShopifyProductClient. */
interface ShopifyAdminClient {
    fun shop(session: ShopifySession, token: String): ShopifyShop
}
