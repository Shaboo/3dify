package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.shared.exception.BadRequestException
import com.thridify.shared.exception.UnauthorizedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

class ShopifyHmacWebhookVerifierTest {
    private val config = ShopifyProperties(enabled = true, clientId = "client", clientSecret = "0123456789abcdef0123456789abcdef")
    private val verifier = ShopifyHmacWebhookVerifier(config, jacksonObjectMapper())
    private val body = """{"id":123,"myshopify_domain":"alpha.myshopify.com","name":"Café"}""".toByteArray(Charsets.UTF_8)
    private fun signature(bytes: ByteArray): String = Base64.getEncoder().encodeToString(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(config.clientSecret.toByteArray(), "HmacSHA256")) }.doFinal(bytes))

    @Test
    fun `verifies raw utf8 bytes and resolves signed payload identity`() {
        val event = verifier.verify(body, signature(body), "app/uninstalled", "event-1", "alpha.myshopify.com", "2026-10-04T12:00:00Z")
        assertEquals("gid://shopify/Shop/123", event.shopId)
    }

    @Test
    fun `rejects altered body invalid base64 and header payload shop mismatch`() {
        assertThrows<UnauthorizedException> { verifier.verify(body + byteArrayOf(32), signature(body), "app/uninstalled", "event-1", "alpha.myshopify.com", null) }
        assertThrows<UnauthorizedException> { verifier.verify(body, "not base64", "app/uninstalled", "event-1", "alpha.myshopify.com", null) }
        assertThrows<BadRequestException> { verifier.verify(body, signature(body), "app/uninstalled", "event-1", "beta.myshopify.com", null) }
    }
}
