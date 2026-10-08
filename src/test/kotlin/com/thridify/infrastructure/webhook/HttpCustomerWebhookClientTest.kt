package com.thridify.infrastructure.webhook

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.domain.webhook.WebhookPolicy
import com.thridify.shared.exception.BadRequestException
import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpCustomerWebhookClientTest {
    private val policy = WebhookPolicy()
    private val client = HttpCustomerWebhookClient(jacksonObjectMapper(), policy)

    @Test
    fun `only public HTTPS webhook destinations are accepted`() {
        for (url in listOf("http://example.com", "https://localhost", "https://127.0.0.1", "https://[::1]", "https://example.com:8080", "https://user@example.com", "https://example.com/#fragment")) {
            assertFailsWith<BadRequestException> { policy.destination(url) }
        }
        assertTrue(policy.destination("https://example.com/callback?q=1").host == "example.com")
    }

    @Test
    fun `private resolved destinations including mapped IPv6 and carrier networks are blocked`() {
        for (ip in listOf("0.0.0.0", "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1", "2001:0::1")) {
            assertFalse(client.publicAddress(InetAddress.getByName(ip)), ip)
        }
        for (ip in listOf("8.8.8.8", "2606:4700:4700::1111")) assertTrue(client.publicAddress(InetAddress.getByName(ip)), ip)
    }
}
