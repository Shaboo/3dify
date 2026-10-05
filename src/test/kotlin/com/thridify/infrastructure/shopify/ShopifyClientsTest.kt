package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.domain.shopify.ShopifySession
import com.thridify.shared.exception.ApiException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopifyClientsTest {
    private val mapper = jacksonObjectMapper()
    private val http: ShopifyHttpClient = mockk()
    private val config = ShopifyProperties(enabled = true, clientId = "client", clientSecret = "secret", appId = "gid://shopify/App/1", appHandle = "thridify", partnerOrgId = "123", partnerAccessToken = "partner-token", partnerRequestIntervalMs = 0)

    @Test
    fun `installation uses online token exchange and verifies the returned shop`() {
        val form = slot<Any>()
        every { http.post("https://alpha.myshopify.com/admin/oauth/access_token", any(), capture(form)) } returns mapper.readTree("""{"access_token":"merchant-token"}""")
        every { http.post("https://alpha.myshopify.com/admin/api/2026-07/graphql.json", any(), any()) } returns mapper.readTree("""{"data":{"shop":{"id":"gid://shopify/Shop/7","name":"Alpha","myshopifyDomain":"alpha.myshopify.com"}}}""")
        val shop = ShopifyInstallationClient(config, http).shop(ShopifySession("alpha.myshopify.com", "42"), "id-token")
        assertEquals("gid://shopify/Shop/7", shop.id)
        assertTrue(form.captured.toString().contains("online-access-token"))
        assertTrue(form.captured.toString().contains("subject_token=id-token"))
        verify { http.post(any(), match { it["X-Shopify-Access-Token"] == "merchant-token" }, any()) }
    }

    @Test
    fun `installation rejects a different shop identity`() {
        every { http.post(any(), any(), any()) } returnsMany listOf(mapper.readTree("""{"access_token":"merchant-token"}"""), mapper.readTree("""{"data":{"shop":{"id":"gid://shopify/Shop/7","myshopifyDomain":"beta.myshopify.com"}}}"""))
        assertThrows<ApiException> { ShopifyInstallationClient(config, http).shop(ShopifySession("alpha.myshopify.com", "42"), "id-token") }
    }

    @Test
    fun `native managed contracts have no invented external subscription identifier`() {
        every { http.post(any(), any(), any()) } returns mapper.readTree("""{"data":{"activeSubscription":{"shop":{"id":"gid://shopify/Shop/7"},"billingPeriod":"EVERY_30_DAYS","trialEndsAt":null,"legacySubscriptionId":null,"currentBillingCycle":{"startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-31T00:00:00Z"},"items":[{"handle":"pro"}]},"events":{"edges":[]}}}""")
        val snapshot = ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7")!!
        assertEquals(null, snapshot.externalSubscriptionId)
        assertEquals(setOf("pro"), snapshot.offerHandles)
        assertEquals("active", snapshot.status)
        verify { http.post("https://partners.shopify.com/123/api/2026-07/graphql.json", match { it["X-Shopify-Access-Token"] == "partner-token" }, any()) }
    }

    @Test
    fun `trial and frozen state map without trusting frontend plan claims`() {
        val trial = """{"data":{"activeSubscription":{"shop":{"id":"gid://shopify/Shop/7"},"billingPeriod":"EVERY_30_DAYS","trialEndsAt":"2026-10-20T00:00:00Z","currentBillingCycle":null,"items":[{"handle":"pro"}]},"events":{"edges":[]}}}"""
        every { http.post(any(), any(), any()) } returns mapper.readTree(trial)
        assertEquals("trialing", ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7")!!.status)
        every { http.post(any(), any(), any()) } returns mapper.readTree(trial.replace("\"edges\":[]", "\"edges\":[{\"node\":{\"eventType\":\"SUBSCRIPTION_FROZEN\"}}]"))
        assertEquals("frozen", ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7")!!.status)
    }

    @Test
    fun `missing subscription is distinct from malformed or foreign-shop billing`() {
        every { http.post(any(), any(), any()) } returns mapper.readTree("""{"data":{"activeSubscription":null}}""")
        assertEquals(null, ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7"))
        every { http.post(any(), any(), any()) } returns mapper.readTree("""{"data":{}}""")
        assertThrows<ApiException> { ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7") }
        every { http.post(any(), any(), any()) } returns mapper.readTree("""{"data":{"activeSubscription":{"shop":{"id":"gid://shopify/Shop/99"}}}}""")
        assertThrows<ApiException> { ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7") }
    }

    @Test
    fun `freeze older than a year is found in earlier explicit history windows`() {
        val response = mapper.readTree("""{"data":{"activeSubscription":{"shop":{"id":"gid://shopify/Shop/7"},"billingPeriod":"ANNUAL","currentBillingCycle":{"startTime":"2026-01-01T00:00:00Z","endTime":"2027-01-01T00:00:00Z"},"items":[{"handle":"pro"}]},"events":{"edges":[]}}}""")
        every { http.post(any(), any(), any()) } returnsMany listOf(response, mapper.readTree("""{"data":{"events":{"edges":[{"node":{"eventType":"SUBSCRIPTION_FROZEN"}}]}}}"""))
        assertEquals("frozen", ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7")!!.status)
        verify(exactly = 2) { http.post(any(), any(), match { it.toString().contains("occurredAtMin") && it.toString().contains("occurredAtMax") }) }
    }

    @Test
    fun `malformed lifecycle and billing dates fail closed`() {
        val response = """{"data":{"activeSubscription":{"shop":{"id":"gid://shopify/Shop/7"},"billingPeriod":"EVERY_30_DAYS","currentBillingCycle":{"startTime":"invalid","endTime":"2026-10-31T00:00:00Z"},"items":[{"handle":"pro"}]},"events":{"edges":[{"node":{"eventType":"SUBSCRIPTION_CREATED"}}]}}}"""
        every { http.post(any(), any(), any()) } returns mapper.readTree(response)
        assertEquals(502, assertThrows<ApiException> { ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7") }.statusCode)
        every { http.post(any(), any(), any()) } returns mapper.readTree(response.replace("\"edges\":[{\"node\":{\"eventType\":\"SUBSCRIPTION_CREATED\"}}]", "\"edges\":null"))
        assertEquals(502, assertThrows<ApiException> { ShopifyPartnerBillingClient(config, http).currentSubscription("gid://shopify/Shop/7") }.statusCode)
    }

    @Test
    fun `empty non-object and malformed HTTP responses map to upstream errors`() {
        val client = ShopifyHttpClient(mapper)
        for (body in listOf("", "null", "[]", "true", "{broken")) {
            assertEquals(502, assertThrows<ApiException> { client.parse(body) }.statusCode)
        }
        assertEquals(403, assertThrows<ApiException> { client.parse("""{"errors":[{"extensions":{"code":"ACCESS_DENIED"}}]}""") }.statusCode)
        assertEquals(503, assertThrows<ApiException> { client.parse("""{"errors":[{"extensions":{"code":"THROTTLED"}}]}""") }.statusCode)
        assertEquals(401, assertThrows<ApiException> { client.parse("""{"errors":[{"extensions":{"code":"UNAUTHENTICATED"}}]}""") }.statusCode)
    }

    @Test
    fun `pricing url is configured by backend and bound to verified shop`() {
        assertEquals("https://admin.shopify.com/store/alpha/charges/thridify/pricing_plans", ShopifyPartnerBillingClient(config, http).pricingUrl("alpha.myshopify.com"))
        assertThrows<ApiException> { ShopifyPartnerBillingClient(config, http).pricingUrl("attacker.example") }
    }
}
