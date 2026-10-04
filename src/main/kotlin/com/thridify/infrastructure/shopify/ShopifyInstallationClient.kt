package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyAdminClient
import com.thridify.domain.shopify.ShopifySession
import com.thridify.domain.shopify.ShopifyShop
import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class ShopifyInstallationClient(private val config: ShopifyProperties, private val http: ShopifyHttpClient) : ShopifyAdminClient {
    override fun shop(session: ShopifySession, token: String): ShopifyShop {
        config.requireEnabled()
        val form = mapOf(
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret,
            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
            "subject_token" to token,
            "subject_token_type" to "urn:ietf:params:oauth:token-type:id_token",
            "requested_token_type" to "urn:shopify:params:oauth:token-type:online-access-token",
        ).entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
        val access = http.post("https://${session.shopDomain}/admin/oauth/access_token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), form)
        val accessToken = access.path("access_token").asText()
        if (accessToken.isBlank()) throw ApiException(502, "Shopify did not authorize the installation")
        val result = http.post(
            "https://${session.shopDomain}/admin/api/${config.apiVersion}/graphql.json",
            mapOf("Content-Type" to "application/json", "X-Shopify-Access-Token" to accessToken),
            mapOf("query" to "{ shop { id name myshopifyDomain } }"),
        ).path("data").path("shop")
        val id = result.path("id").asText()
        val domain = result.path("myshopifyDomain").asText()
        if (!Regex("gid://shopify/Shop/[0-9]+").matches(id) || domain != session.shopDomain) throw ApiException(502, "Shopify installation identity mismatch")
        return ShopifyShop(id, domain, result.path("name").asText(domain))
    }
}
