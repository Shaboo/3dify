package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.shopify.ShopifyWebhook
import com.thridify.domain.shopify.ShopifyWebhookVerifier
import com.thridify.shared.exception.BadRequestException
import com.thridify.shared.exception.UnauthorizedException
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class ShopifyHmacWebhookVerifier(private val config: ShopifyProperties, private val mapper: ObjectMapper) : ShopifyWebhookVerifier {
    override fun verify(body: ByteArray, signature: String, topic: String, eventId: String, shopDomain: String, triggeredAt: String?): ShopifyWebhook {
        config.requireEnabled()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(config.clientSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        val supplied = try {
            Base64.getDecoder().decode(signature)
        } catch (_: Exception) {
            throw UnauthorizedException("Invalid Shopify webhook signature")
        }
        if (!MessageDigest.isEqual(mac.doFinal(body), supplied)) throw UnauthorizedException("Invalid Shopify webhook signature")
        if (topic !in setOf("app/uninstalled", "customers/data_request", "customers/redact", "shop/redact") || eventId.isBlank() || eventId.length > 255) throw BadRequestException("Invalid Shopify webhook metadata")
        if (!Regex("[a-z0-9][a-z0-9-]*[.]myshopify[.]com").matches(shopDomain)) throw BadRequestException("Invalid Shopify shop domain")
        val payload = try {
            mapper.readTree(body)
        } catch (_: Exception) {
            throw BadRequestException("Invalid Shopify webhook payload")
        }
        val numericId = if (topic == "app/uninstalled") payload.path("id").asText() else payload.path("shop_id").asText()
        if (!Regex("[0-9]+").matches(numericId)) throw BadRequestException("Missing Shopify shop ID")
        val domain = if (topic == "app/uninstalled") payload.path("myshopify_domain").asText() else payload.path("shop_domain").asText()
        if (domain != shopDomain) throw BadRequestException("Shopify webhook shop mismatch")
        val time = try {
            triggeredAt?.let(OffsetDateTime::parse) ?: OffsetDateTime.now()
        } catch (_: Exception) {
            throw BadRequestException("Invalid webhook timestamp")
        }
        return ShopifyWebhook(eventId, topic, "gid://shopify/Shop/$numericId", shopDomain, time)
    }
}
