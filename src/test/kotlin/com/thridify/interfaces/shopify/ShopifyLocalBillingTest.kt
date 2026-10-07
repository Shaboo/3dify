package com.thridify.interfaces.shopify

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.generation.ImageStorage
import com.thridify.domain.shopify.ShopifyAdminClient
import com.thridify.domain.shopify.ShopifyProductClient
import com.thridify.domain.shopify.ShopifySession
import com.thridify.domain.shopify.ShopifyShop
import com.thridify.infrastructure.shopify.ShopifyProperties
import io.jsonwebtoken.Jwts
import io.mockk.Called
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

@ActiveProfiles("local")
@TestPropertySource(properties = ["shopify.enabled=true", "shopify.jobs-enabled=false", "shopify.billing-mode=local-test", "shopify.local-test-shop-id=gid://shopify/Shop/1", "shopify.local-test-shop-domain=alpha.myshopify.com", "shopify.local-test-generation-limit=2", "shopify.client-id=test-client", "shopify.client-secret=0123456789abcdef0123456789abcdef"])
@Import(ShopifyLocalBillingTest.Ports::class)
class ShopifyLocalBillingTest : IntegrationTestBase() {
    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var admin: ShopifyAdminClient

    @Autowired private lateinit var products: ShopifyProductClient

    @Autowired private lateinit var storage: ImageStorage

    @Autowired private lateinit var providers: GenerationProviderRegistry

    @Autowired private lateinit var config: ShopifyProperties

    @TestConfiguration
    class Ports {
        @Bean @Primary
        fun admin(): ShopifyAdminClient = mockk()

        @Bean @Primary
        fun products(): ShopifyProductClient = mockk()

        @Bean @Primary
        fun storage(): ImageStorage = mockk()

        @Bean @Primary
        fun providers(): GenerationProviderRegistry = mockk()
    }

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
        config.localTestGenerationLimit = 2
        clearMocks(admin, products, storage, providers)
        every { admin.shop(any(), any()) } answers {
            val domain = firstArg<ShopifySession>().shopDomain
            ShopifyShop(if (domain == "alpha.myshopify.com") "gid://shopify/Shop/1" else "gid://shopify/Shop/2", domain, "Test")
        }
        every { products.product(any(), any(), any(), true) } answers {
            com.thridify.domain.shopify.ShopifyProduct(thirdArg(), "Test", listOf(com.thridify.domain.shopify.ShopifyProductPhoto("gid://shopify/MediaImage/11", "https://cdn.shopify.com/test.png", null)))
        }
        every { products.downloadPhotos(any(), any()) } returns listOf(com.thridify.domain.shopify.ShopifyPhotoData(byteArrayOf(1), "test.png", "image/png"))
        every { products.prepareBackground(any(), any()) } just Runs
        every { storage.upload(any(), any(), any()) } returns "stored"
        every { providers.current() } returns object : GenerationProviderClient {
            override val name = "meshy"
            override val maxInputImages = 4
            override fun startGeneration(jobId: UUID, inputImages: List<String>) = error("No external generation in tests")
            override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?) = error("No external generation in tests")
            override fun retrieveTask(taskId: String) = error("No external generation in tests")
            override fun deleteTask(taskId: String) = Unit
        }
    }

    private fun token(shop: String = "alpha") = Jwts.builder().subject("42").audience().add("test-client").and()
        .issuer("https://$shop.myshopify.com/admin").claim("dest", "https://$shop.myshopify.com")
        .issuedAt(Date.from(Instant.now())).notBefore(Date.from(Instant.now().minusSeconds(1))).expiration(Date.from(Instant.now().plusSeconds(60)))
        .signWith(SecretKeySpec(config.clientSecret.toByteArray(), "HmacSHA256"), Jwts.SIG.HS256).compact()

    private fun connect(shop: String = "alpha") = mockMvc.perform(post("/shopify/api/connection").header("Authorization", "Bearer ${token(shop)}")).andExpect(status().isOk)
    private fun subscription(shop: String = "alpha") = mockMvc.perform(get("/shopify/api/subscription").header("Authorization", "Bearer ${token(shop)}"))
    private fun generate(key: UUID = UUID.randomUUID(), shop: String = "alpha") = mockMvc.perform(
        post("/shopify/api/products/1/models").contentType("application/json").content("""{"imageIds":["gid://shopify/MediaImage/11"]}""")
            .header("Authorization", "Bearer ${token(shop)}").header("Idempotency-Key", key),
    )
    private fun count(table: String) = dsl.fetchOne("SELECT count(*) AS count FROM $table")!!.get("count", Int::class.java)

    @Test
    fun `subscription works without Partner credentials or offers and reports local testing`() {
        connect()
        subscription().andExpect(status().isOk).andExpect(jsonPath("$.localTesting").value(true))
            .andExpect(jsonPath("$.generationLimit").value(2)).andExpect(jsonPath("$.generationsConsumed").value(0))
            .andExpect(jsonPath("$.pricingUrl").value(""))
        assertEquals(0, count("plan_offers"))
        assertEquals(false, dsl.fetchOne("SELECT is_active FROM plans WHERE name = 'shopify-local-test'")!!.get("is_active", Boolean::class.java))
    }

    @Test
    fun `product generation binds attachment atomically and retries keep one allowance job and outbox message`() {
        connect()
        val key = UUID.randomUUID()
        val first = generate(key).andExpect(status().isAccepted).andReturn()
        generate(key).andExpect(status().isAccepted).andExpect(jsonPath("$.jobId").value(mapper.readTree(first.response.contentAsString).path("jobId").asText()))
        connect()
        subscription().andExpect(status().isOk).andExpect(jsonPath("$.generationsConsumed").value(1))
        assertEquals(1, count("jobs"))
        assertEquals(1, count("outbox_messages"))
        assertEquals(1, count("shopify_model_attachments"))
        assertEquals(1, count("usage_periods"))
        generate().andExpect(status().isAccepted)
        generate().andExpect(status().isTooManyRequests)
        subscription().andExpect(status().isOk).andExpect(jsonPath("$.generationsConsumed").value(2))
        assertEquals(2, count("jobs"))
    }

    @Test
    fun `other authenticated stores receive no test allowance`() {
        connect("beta")
        subscription("beta").andExpect(status().isForbidden)
        generate(shop = "beta").andExpect(status().isForbidden)
        assertEquals(0, count("subscriptions"))
        assertEquals(0, count("usage_periods"))
        assertEquals(0, count("jobs"))
        verify { products wasNot Called }
        verify { storage wasNot Called }
    }

    @Test
    fun `lowering test quota never erases consumed allowance or resets the period`() {
        connect()
        generate().andExpect(status().isAccepted)
        generate().andExpect(status().isAccepted)
        config.localTestGenerationLimit = 1
        subscription().andExpect(status().isOk).andExpect(jsonPath("$.generationsConsumed").value(2))
        assertEquals(1, count("usage_periods"))
        generate().andExpect(status().isTooManyRequests)
    }
}
