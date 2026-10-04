package com.thridify.domain.shopify

interface ShopifySessionVerifier {
    fun verify(token: String): ShopifySession
}
