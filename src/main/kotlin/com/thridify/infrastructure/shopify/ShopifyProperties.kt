package com.thridify.infrastructure.shopify

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("shopify")
data class ShopifyProperties(
    var enabled: Boolean = false,
    var clientId: String = "",
    var clientSecret: String = "",
    var appId: String = "",
    var appHandle: String = "",
    var credentialEncryptionKey: String = "",
    var partnerOrgId: String = "",
    var partnerAccessToken: String = "",
    var partnerRequestIntervalMs: Long = 300,
    var apiVersion: String = "2026-07",
    var frontendOrigins: List<String> = emptyList(),
) {
    fun requireEnabled() {
        if (!enabled || clientId.isBlank() || clientSecret.isBlank()) throw com.thridify.shared.exception.ApiException(503, "Shopify integration is not configured")
    }
}
