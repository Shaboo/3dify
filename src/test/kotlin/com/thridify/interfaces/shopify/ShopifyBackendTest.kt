package com.thridify.interfaces.shopify

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.application.service.shopify.redact.RedactShopifyDataApplicationService
import com.thridify.domain.generation.ImageStorage
import com.thridify.domain.shopify.ShopifyAdminClient
import com.thridify.domain.shopify.ShopifyAssetDeletionClient
import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyBillingSnapshot
import com.thridify.domain.shopify.ShopifySession
import com.thridify.domain.shopify.ShopifyShop
import io.jsonwebtoken.Jwts
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

@TestPropertySource(properties = ["shopify.enabled=true", "shopify.jobs-enabled=false", "shopify.client-id=test-client", "shopify.client-secret=0123456789abcdef0123456789abcdef", "shopify.frontend-origins=https://frontend.example"])
@Import(ShopifyBackendTest.Ports::class)
class ShopifyBackendTest : IntegrationTestBase() {
    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var admin: ShopifyAdminClient

    @Autowired private lateinit var billing: ShopifyBillingClient

    @Autowired private lateinit var storage: ImageStorage

    @Autowired private lateinit var deletion: ShopifyAssetDeletionClient

    @Autowired private lateinit var redact: RedactShopifyDataApplicationService
    private val secret = "0123456789abcdef0123456789abcdef"
    private val start = OffsetDateTime.now().minusDays(1)
    private val end = start.plusDays(30)

    @TestConfiguration
    class Ports {
        @Bean @Primary
        fun admin(): ShopifyAdminClient = mockk()

        @Bean @Primary
        fun billing(): ShopifyBillingClient = mockk()

        @Bean @Primary
        fun storage(): ImageStorage = mockk()

        @Bean @Primary
        fun deletion(): ShopifyAssetDeletionClient = mockk()
    }

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
        clearMocks(admin, billing, storage, deletion)
        dsl.execute("UPDATE plans SET monthly_quota = 2 WHERE name = 'pro'")
        dsl.execute("INSERT INTO plan_offers (plan_id, provider, external_offer_id) SELECT id, 'shopify', 'pro' FROM plans WHERE name = 'pro'")
        every { admin.shop(any(), any()) } answers {
            val session = firstArg<ShopifySession>()
            ShopifyShop(if (session.shopDomain == "alpha.myshopify.com") "gid://shopify/Shop/1" else "gid://shopify/Shop/2", session.shopDomain, "Shop")
        }
        every { billing.currentSubscription(any()) } returns ShopifyBillingSnapshot(setOf("pro"), "monthly", "active", start, end, null)
        every { billing.pricingUrl(any()) } returns "https://admin.shopify.com/store/alpha/charges/thridify/pricing_plans"
        every { storage.upload(any(), any(), any()) } returns "stored"
        every { deletion.deleteInput(any()) } just Runs
    }

    private fun token(shop: String = "alpha", audience: String = "test-client"): String = Jwts.builder().subject("42").audience().add(audience).and()
        .issuer("https://$shop.myshopify.com/admin").claim("dest", "https://$shop.myshopify.com")
        .issuedAt(Date.from(Instant.now())).notBefore(Date.from(Instant.now().minusSeconds(1))).expiration(Date.from(Instant.now().plusSeconds(60)))
        .signWith(SecretKeySpec(secret.toByteArray(), "HmacSHA256"), Jwts.SIG.HS256).compact()

    private fun connect(shop: String = "alpha"): UUID {
        val result = mockMvc.perform(post("/shopify/api/connection").header("Authorization", "Bearer ${token(shop)}"))
            .andExpect(status().isOk).andReturn()
        return UUID.fromString(mapper.readTree(result.response.contentAsString).path("billingScopeId").asText())
    }

    private fun generate(shop: String = "alpha", request: UUID = UUID.randomUUID()) = mockMvc.perform(
        multipart("/shopify/api/models").file(MockMultipartFile("image1", "one.png", "image/png", byteArrayOf(1)))
            .file(MockMultipartFile("image2", "two.png", "image/png", byteArrayOf(2)))
            .header("Authorization", "Bearer ${token(shop)}").header("Idempotency-Key", request),
    )

    private fun webhook(topic: String, shop: String = "alpha", id: Int = 1, event: String = UUID.randomUUID().toString(), occurred: OffsetDateTime = OffsetDateTime.now()) {
        val body = if (topic == "app/uninstalled") """{"id":$id,"myshopify_domain":"$shop.myshopify.com"}""" else """{"shop_id":$id,"shop_domain":"$shop.myshopify.com"}"""
        val signature = Base64.getEncoder().encodeToString(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }.doFinal(body.toByteArray()))
        mockMvc.perform(
            post("/shopify/webhooks").contentType("application/json").content(body)
                .header("X-Shopify-Hmac-Sha256", signature).header("X-Shopify-Topic", topic).header("X-Shopify-Event-Id", event)
                .header("X-Shopify-Shop-Domain", "$shop.myshopify.com").header("X-Shopify-Triggered-At", occurred.toString()),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `installation is idempotent and does not fabricate a password user`() {
        assertEquals(connect(), connect())
        assertEquals(1, count("workspaces"))
        assertEquals(1, count("platform_connections"))
        assertEquals(0, count("users"))
    }

    @Test
    fun `missing forged or wrong-app token cannot provision or access a store`() {
        mockMvc.perform(post("/shopify/api/connection")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/shopify/api/connection").header("Authorization", "Bearer forged")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/shopify/api/connection").header("Authorization", "Bearer ${token(audience = "wrong-client")}")).andExpect(status().isUnauthorized)
        assertEquals(0, count("workspaces"))
        verify(exactly = 0) { admin.shop(any(), any()) }
    }

    @Test
    fun `store quotas are independent and retries produce one job and one message`() {
        val first = connect()
        val second = connect("beta")
        val request = UUID.randomUUID()
        val result = generate(request = request).andExpect(status().isAccepted).andReturn()
        val job = mapper.readTree(result.response.contentAsString).path("jobId").asText()
        generate(request = request).andExpect(status().isAccepted).andExpect(jsonPath("$.jobId").value(job))
        generate().andExpect(status().isAccepted)
        generate().andExpect(status().isTooManyRequests)
        generate("beta").andExpect(status().isAccepted)
        assertEquals(2, consumed(first))
        assertEquals(1, consumed(second))
        assertEquals(3, count("jobs"))
        assertEquals(3, count("outbox_messages"))
        verify(exactly = 6) { storage.upload(any(), any(), any()) }
    }

    @Test
    fun `models cannot be read from another store`() {
        connect()
        connect("beta")
        val result = generate().andExpect(status().isAccepted).andReturn()
        val id = mapper.readTree(result.response.contentAsString).path("jobId").asText()
        mockMvc.perform(get("/shopify/api/models/$id").header("Authorization", "Bearer ${token("beta")}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/shopify/api/models").header("Authorization", "Bearer ${token("beta")}")).andExpect(status().isOk).andExpect(jsonPath("$.models.length()").value(0))
        mockMvc.perform(get("/shopify/api/models/$id").header("Authorization", "Bearer ${token()}")).andExpect(status().isOk)
    }

    @Test
    fun `unmapped missing and frozen billing fail before upload`() {
        connect()
        every { billing.currentSubscription(any()) } returns null
        generate().andExpect(status().isPaymentRequired)
        every { billing.currentSubscription(any()) } returns ShopifyBillingSnapshot(setOf("unconfigured"), "monthly", "active", start, end, null)
        generate().andExpect(status().isPaymentRequired)
        every { billing.currentSubscription(any()) } returns ShopifyBillingSnapshot(setOf("pro"), "monthly", "frozen", start, end, null)
        generate().andExpect(status().isPaymentRequired)
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
    }

    @Test
    fun `uninstall is deduplicated and disables only that store`() {
        connect()
        connect("beta")
        generate().andExpect(status().isAccepted)
        generate("beta").andExpect(status().isAccepted)
        webhook("app/uninstalled", event = "same-event")
        webhook("app/uninstalled", event = "same-event")
        generate().andExpect(status().isForbidden)
        generate("beta").andExpect(status().isAccepted)
        assertEquals(1, count("shopify_webhook_receipts"))
    }

    @Test
    fun `invalid webhook signature is rejected before storage or lifecycle updates`() {
        connect()
        mockMvc.perform(post("/shopify/webhooks").contentType("application/json").content("{}").header("X-Shopify-Hmac-Sha256", "bad").header("X-Shopify-Topic", "app/uninstalled").header("X-Shopify-Event-Id", "event").header("X-Shopify-Shop-Domain", "alpha.myshopify.com"))
            .andExpect(status().isUnauthorized)
        assertEquals(0, count("shopify_webhook_receipts"))
        generate().andExpect(status().isAccepted)
    }

    @Test
    fun `privacy deletion retains other stores and remains pending on storage failure`() {
        connect()
        connect("beta")
        generate().andExpect(status().isAccepted)
        generate("beta").andExpect(status().isAccepted)
        webhook("app/uninstalled")
        webhook("shop/redact", event = "redact-event")
        every { deletion.deleteInput(any()) } throws IllegalStateException("storage unavailable")
        redact.execute()
        assertEquals(2, count("jobs"))
        assertEquals(null, dsl.fetchOne("SELECT completed_at FROM shopify_webhook_receipts WHERE event_id = 'redact-event'")!!.get("completed_at"))
        mockMvc.perform(post("/shopify/api/connection").header("Authorization", "Bearer ${token()}")).andExpect(status().isConflict)
        every { deletion.deleteInput(any()) } just Runs
        redact.execute()
        assertEquals(1, count("jobs"))
        assertEquals(1, count("platform_connections"))
        assertEquals(1, count("outbox_messages"))
        assertEquals(null, dsl.fetchOne("SELECT shop_id FROM shopify_webhook_receipts WHERE event_id = 'redact-event'")!!.get("shop_id"))
    }

    @Test
    fun `late uninstall and privacy events cannot erase a reinstalled store`() {
        connect()
        generate().andExpect(status().isAccepted)
        val oldEventTime = OffsetDateTime.now()
        webhook("app/uninstalled", occurred = oldEventTime)
        connect()
        webhook("app/uninstalled", occurred = oldEventTime)
        webhook("shop/redact")
        redact.execute()
        assertEquals(1, count("jobs"))
        mockMvc.perform(get("/shopify/api/models").header("Authorization", "Bearer ${token()}")).andExpect(status().isOk)
    }

    @Test
    fun `billing state exposes hosted pricing while rejecting unconfigured frontend origins`() {
        connect()
        mockMvc.perform(get("/shopify/api/subscription").header("Authorization", "Bearer ${token()}")).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("active")).andExpect(jsonPath("$.generationLimit").value(2)).andExpect(jsonPath("$.pricingUrl").exists())
        mockMvc.perform(get("/shopify/api/models").header("Authorization", "Bearer ${token()}").header("Origin", "https://attacker.example")).andExpect(status().isForbidden)
        mockMvc.perform(get("/shopify/api/models").header("Authorization", "Bearer ${token()}").header("Origin", "https://frontend.example")).andExpect(status().isOk)
    }

    private fun count(table: String): Int = dsl.fetchOne("SELECT count(*) AS total FROM $table")!!.get("total", Int::class.java)!!
    private fun consumed(scope: UUID): Int = dsl.fetchOne("SELECT generations_consumed FROM usage_periods WHERE billing_scope_id = ?", scope)!!.get("generations_consumed", Int::class.java)!!
}
