package com.thridify.interfaces.shopify

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.application.service.shopify.redact.RedactShopifyDataApplicationService
import com.thridify.domain.generation.GenerationOutputStorage
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderRegistry
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

    @Autowired private lateinit var providers: GenerationProviderRegistry

    @Autowired private lateinit var retainedOutputs: GenerationOutputStorage

    @Autowired private lateinit var products: com.thridify.domain.shopify.ShopifyProductClient

    @Autowired private lateinit var attach: com.thridify.application.service.shopify.attach.AttachShopifyModelsApplicationService

    @Autowired private lateinit var jobs: com.thridify.domain.job.JobRepository

    @Autowired private lateinit var attachmentRepository: com.thridify.domain.shopify.ShopifyAttachmentRepository

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

        @Bean @Primary
        fun providers(): GenerationProviderRegistry = mockk()

        @Bean @Primary
        fun products(): com.thridify.domain.shopify.ShopifyProductClient = mockk()

        @Bean @Primary
        fun outputs(): GenerationOutputStorage = mockk(relaxed = true)
    }

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
        clearMocks(admin, billing, storage, deletion, providers, retainedOutputs, products)
        dsl.execute("UPDATE plans SET monthly_quota = 2 WHERE name = 'pro'")
        dsl.execute("INSERT INTO plan_offers (plan_id, provider, external_offer_id) SELECT id, 'shopify', 'pro' FROM plans WHERE name = 'pro'")
        every { admin.shop(any(), any()) } answers {
            val session = firstArg<ShopifySession>()
            ShopifyShop(if (session.shopDomain == "alpha.myshopify.com") "gid://shopify/Shop/1" else "gid://shopify/Shop/2", session.shopDomain, "Shop")
        }
        every { products.product(any(), any(), any(), any()) } answers {
            com.thridify.domain.shopify.ShopifyProduct(thirdArg(), "Test product", List(4) { com.thridify.domain.shopify.ShopifyProductPhoto("gid://shopify/MediaImage/${it + 11}", "https://cdn.shopify.com/$it.png", null) })
        }
        every { products.downloadPhotos(any(), any()) } answers {
            secondArg<List<String>>().map { com.thridify.domain.shopify.ShopifyPhotoData(byteArrayOf(1), "photo.png", "image/png") }
        }
        every { products.prepareBackground(any(), any()) } just Runs
        every { products.findModel(any(), any(), any()) } returns null
        every { products.attachModel(any(), any(), any(), any()) } returns com.thridify.domain.shopify.ShopifyAttachedModel("gid://shopify/Model3d/1", "READY")
        every { billing.currentSubscription(any()) } returns ShopifyBillingSnapshot(setOf("pro"), "monthly", "active", start, end, null)
        every { billing.pricingUrl(any()) } returns "https://admin.shopify.com/store/alpha/charges/thridify/pricing_plans"
        every { providers.current() } returns object : GenerationProviderClient {
            override val name = "meshy"
            override val maxInputImages = 4
            override fun startGeneration(jobId: UUID, inputImages: List<String>) = error("Unused external submission")
            override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String = error("Unexpected external generation")
            override fun retrieveTask(taskId: String) = error("Unexpected external retrieval")
            override fun deleteTask(taskId: String) = Unit
        }
        every { storage.upload(any(), any(), any()) } returns "stored"
        every { deletion.deleteInput(any()) } just Runs
        every { deletion.deleteOutput(any()) } just Runs
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

    private fun productGeneration(requestId: UUID = UUID.randomUUID(), productId: Int = 1): UUID {
        val response = mockMvc.perform(
            post("/shopify/api/products/$productId/models").contentType("application/json")
                .content("""{"imageIds":["gid://shopify/MediaImage/11","gid://shopify/MediaImage/12"]}""")
                .header("Authorization", "Bearer ${token()}").header("Idempotency-Key", requestId),
        ).andExpect(status().isAccepted).andReturn()
        return UUID.fromString(mapper.readTree(response.response.contentAsString).path("jobId").asText())
    }
    private fun readyForAttachment(id: UUID) {
        jobs.markSuccess(id, "https://models.example/outputs/$id/model.glb", "https://models.example/outputs/$id/model.usdz")
    }
    private fun makeAttachmentDue(id: UUID) {
        dsl.execute("UPDATE shopify_model_attachments SET next_attempt_at = now() WHERE job_id = ?", id)
    }
    private fun attachmentStatus(id: UUID): String = dsl.fetchOne("SELECT status FROM shopify_model_attachments WHERE job_id = ?", id)!!.get("status", String::class.java)!!

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
    fun `Meshy limits are advertised and a fifth photo consumes no allowance or storage`() {
        val scope = connect()
        mockMvc.perform(get("/shopify/api/generation-options").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.maxImages").value(4))
        val request = multipart("/shopify/api/models").header("Authorization", "Bearer ${token()}").header("Idempotency-Key", UUID.randomUUID())
        repeat(5) { request.file(MockMultipartFile("images", "$it.png", "image/png", byteArrayOf(1))) }
        mockMvc.perform(request).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("meshy accepts 1–4 photos; received 5"))
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
        verify(exactly = 0) { billing.currentSubscription(any()) }
        assertEquals(0, count("jobs"))
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM usage_periods WHERE billing_scope_id = ?", scope)!!.get("total", Int::class.java))
    }

    @Test
    fun `single and four photo Shopify requests persist the full list and charge one allowance each`() {
        connect()
        for (size in listOf(1, 4)) {
            val request = multipart("/shopify/api/models").header("Authorization", "Bearer ${token()}").header("Idempotency-Key", UUID.randomUUID())
            repeat(size) { request.file(MockMultipartFile("images", "$it.png", "image/png", byteArrayOf(1))) }
            val response = mockMvc.perform(request).andExpect(status().isAccepted).andReturn()
            val id = UUID.fromString(mapper.readTree(response.response.contentAsString).path("jobId").asText())
            mockMvc.perform(get("/shopify/api/models/$id").header("Authorization", "Bearer ${token()}"))
                .andExpect(status().isOk).andExpect(jsonPath("$.inputImages.length()").value(size))
        }
        assertEquals(2, dsl.fetchOne("SELECT sum(generations_consumed)::int AS total FROM usage_periods")!!.get("total", Int::class.java))
    }

    @Test
    fun `privacy cleanup deletes all four input photos`() {
        connect()
        val request = multipart("/shopify/api/models").header("Authorization", "Bearer ${token()}").header("Idempotency-Key", UUID.randomUUID())
        repeat(4) { request.file(MockMultipartFile("images", "$it.png", "image/png", byteArrayOf(1))) }
        val response = mockMvc.perform(request).andExpect(status().isAccepted).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString).path("jobId").asText())
        val keys = dsl.fetchOne("SELECT input_images FROM jobs WHERE id = ?", id)!!.get("input_images", Array<String>::class.java).toList()
        webhook("app/uninstalled")
        webhook("shop/redact")
        redact.execute()
        keys.forEach { key -> verify(exactly = 1) { deletion.deleteInput(key) } }
        assertEquals(4, keys.size)
        assertEquals(0, count("jobs"))
    }

    @Test
    fun `product photo selection binds once and background attachment needs no browser token`() {
        connect()
        mockMvc.perform(get("/shopify/api/products/1/images").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.images.length()").value(4))
        val requestId = UUID.randomUUID()
        val id = productGeneration(requestId)
        assertEquals(id, productGeneration(requestId))
        assertEquals(1, count("shopify_model_attachments"))
        assertEquals("waiting", attachmentStatus(id))
        verify(exactly = 1) { products.prepareBackground(any(), any()) }
        verify(exactly = 1) { products.product(any(), any(), "gid://shopify/Product/1", true) }
        readyForAttachment(id)
        attach.execute()
        attach.execute()
        assertEquals("attached", attachmentStatus(id))
        verify(exactly = 1) { products.attachModel(any(), "gid://shopify/Product/1", id, any()) }
        mockMvc.perform(get("/shopify/api/models/$id").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.productId").value("gid://shopify/Product/1")).andExpect(jsonPath("$.attachmentStatus").value("attached"))
    }

    @Test
    fun `product-bound upload also attaches automatically and idempotency cannot change its product`() {
        connect()
        val requestId = UUID.randomUUID()
        val request = multipart("/shopify/api/models").file(MockMultipartFile("images", "photo.png", "image/png", byteArrayOf(1)))
            .param("productId", "gid://shopify/Product/1").header("Authorization", "Bearer ${token()}").header("Idempotency-Key", requestId)
        val response = mockMvc.perform(request).andExpect(status().isAccepted).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString).path("jobId").asText())
        mockMvc.perform(
            post("/shopify/api/products/2/models").contentType("application/json").content("""{"imageIds":["gid://shopify/MediaImage/11"]}""")
                .header("Authorization", "Bearer ${token()}").header("Idempotency-Key", requestId),
        ).andExpect(status().isBadRequest)
        assertEquals("waiting", attachmentStatus(id))
        verify(exactly = 0) { products.downloadPhotos(any(), any()) }
    }

    @Test
    fun `ambiguous attachment never blindly creates a second model`() {
        connect()
        val id = productGeneration()
        readyForAttachment(id)
        every { products.attachModel(any(), any(), any(), any()) } throws com.thridify.domain.shopify.ShopifyAttachmentException(true, true, "timeout after product update")
        attach.execute()
        assertEquals("checking", attachmentStatus(id))
        makeAttachmentDue(id)
        attach.execute()
        verify(exactly = 1) { products.attachModel(any(), any(), id, any()) }
        every { products.findModel(any(), any(), id) } returns com.thridify.domain.shopify.ShopifyAttachedModel("media", "READY")
        makeAttachmentDue(id)
        attach.execute()
        assertEquals("attached", attachmentStatus(id))
    }

    @Test
    fun `Shopify processing is polled until ready without re-upload`() {
        connect()
        val id = productGeneration()
        readyForAttachment(id)
        every { products.attachModel(any(), any(), any(), any()) } returns com.thridify.domain.shopify.ShopifyAttachedModel("media", "PROCESSING")
        attach.execute()
        assertEquals("processing", attachmentStatus(id))
        every { products.findModel(any(), any(), id) } returns com.thridify.domain.shopify.ShopifyAttachedModel("media", "READY")
        makeAttachmentDue(id)
        attach.execute()
        assertEquals("attached", attachmentStatus(id))
        verify(exactly = 1) { products.attachModel(any(), any(), id, any()) }
    }

    @Test
    fun `safe failure retries while a crashed uploader only checks for existing media`() {
        connect()
        val id = productGeneration()
        readyForAttachment(id)
        every { products.attachModel(any(), any(), id, any()) } throws com.thridify.domain.shopify.ShopifyAttachmentException(false, true, "storage unavailable")
        attach.execute()
        assertEquals("retrying", attachmentStatus(id))
        every { products.attachModel(any(), any(), id, any()) } returns com.thridify.domain.shopify.ShopifyAttachedModel("media", "READY")
        makeAttachmentDue(id)
        attach.execute()
        assertEquals("attached", attachmentStatus(id))
        val crashed = productGeneration()
        readyForAttachment(crashed)
        dsl.execute("UPDATE shopify_model_attachments SET status = 'checking', lease_until = now() - interval '1 minute' WHERE job_id = ?", crashed)
        attach.execute()
        assertEquals("checking", attachmentStatus(crashed))
        verify(exactly = 0) { products.attachModel(any(), any(), crashed, any()) }
    }

    @Test
    fun `failed generation cancels attachment without suggesting the installed app disconnected`() {
        connect()
        val id = productGeneration()
        jobs.markFailed(id, "Generation provider could not accept the task")
        attach.execute()
        assertEquals("canceled", attachmentStatus(id))
        val message = dsl.fetchOne("SELECT error_message FROM shopify_model_attachments WHERE job_id = ?", id)!!.get("error_message", String::class.java)
        assertEquals("Generation failed; no model is available to attach", message)
        verify(exactly = 0) { products.attachModel(any(), any(), id, any()) }
    }

    @Test
    fun `uninstall cancels attachment and deletes offline credentials before the worker can run`() {
        connect()
        val id = productGeneration()
        readyForAttachment(id)
        dsl.execute(
            """INSERT INTO shopify_offline_credentials(connection_id, installed_at, access_ciphertext, refresh_ciphertext, access_expires_at, refresh_expires_at)
            SELECT id, installed_at, 'encrypted-access', 'encrypted-refresh', now() + interval '1 hour', now() + interval '90 days' FROM platform_connections""",
        )
        webhook("app/uninstalled")
        attach.execute()
        assertEquals("canceled", attachmentStatus(id))
        assertEquals(0, count("shopify_offline_credentials"))
        verify(exactly = 0) { products.attachModel(any(), any(), any(), any()) }
        webhook("shop/redact")
        redact.execute()
        assertEquals(0, count("shopify_model_attachments"))
    }

    @Test
    fun `attachment leases prevent two workers from claiming the same completed job`() {
        connect()
        val id = productGeneration()
        readyForAttachment(id)
        val first = attachmentRepository.claimDue(1)
        assertEquals(1, first.size)
        assertEquals(emptyList(), attachmentRepository.claimDue(1))
        dsl.execute("UPDATE shopify_model_attachments SET lease_until = now() - interval '1 minute' WHERE job_id = ?", id)
        val second = attachmentRepository.claimDue(1).single()
        attachmentRepository.finish(first.single(), "attached", "stale-media", null)
        assertEquals("waiting", attachmentStatus(id))
        attachmentRepository.finish(second, "attached", "current-media", null)
        assertEquals("attached", attachmentStatus(id))
    }

    @Test
    fun `product requests reject missing staff write permissions before creating a job`() {
        connect()
        every { products.product(any(), any(), any(), true) } throws com.thridify.shared.exception.ApiException(403, "Product permission denied")
        mockMvc.perform(
            post("/shopify/api/products/1/models").contentType("application/json").content("""{"imageIds":["gid://shopify/MediaImage/11"]}""")
                .header("Authorization", "Bearer ${token()}").header("Idempotency-Key", UUID.randomUUID()),
        ).andExpect(status().isForbidden)
        verify(exactly = 0) { products.prepareBackground(any(), any()) }
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
        assertEquals(0, count("jobs"))
    }

    @Test
    fun `product extension origin is allowed while untrusted origins remain blocked`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/shopify/api/products/1/images")
                .header("Origin", "https://extensions.shopifycdn.com").header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"),
        )
            .andExpect(status().isOk).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "https://extensions.shopifycdn.com"))
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/shopify/api/products/1/images")
                .header("Origin", "https://untrusted.example").header("Access-Control-Request-Method", "GET"),
        ).andExpect(status().isForbidden)
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
    fun `concurrent retries at the quota boundary return the same job and clean losing uploads`() {
        val scope = connect()
        dsl.execute("UPDATE plans SET monthly_quota = 1 WHERE name = 'pro'")
        val ready = java.util.concurrent.CountDownLatch(2)
        every { storage.upload(match { it.endsWith("one.png") }, any(), any()) } answers {
            ready.countDown()
            check(ready.await(10, java.util.concurrent.TimeUnit.SECONDS))
            "stored"
        }
        val request = UUID.randomUUID()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit<String> {
                    val response = generate(request = request).andExpect(status().isAccepted).andReturn()
                    mapper.readTree(response.response.contentAsString).path("jobId").asText()
                }
            }
            assertEquals(futures[0].get(20, java.util.concurrent.TimeUnit.SECONDS), futures[1].get(20, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1, consumed(scope))
            assertEquals(1, count("jobs"))
            assertEquals(1, count("job_history"))
            assertEquals(1, count("outbox_messages"))
            verify(exactly = 2) { deletion.deleteInput(any()) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `annual plan grants only the current monthly allowance and preserves earlier usage`() {
        val scope = connect()
        dsl.execute("UPDATE plan_offers SET billing_interval = 'yearly' WHERE provider = 'shopify'")
        val annualStart = OffsetDateTime.now().minusMonths(2).minusDays(1)
        every { billing.currentSubscription(any()) } returns ShopifyBillingSnapshot(setOf("pro"), "yearly", "active", annualStart, annualStart.plusYears(1), null)
        dsl.execute("INSERT INTO usage_periods (billing_scope_id, period_start, period_end, generation_limit, generations_consumed) VALUES (?, ?, ?, 2, 2)", scope, java.sql.Timestamp.from(annualStart.toInstant()), java.sql.Timestamp.from(annualStart.plusMonths(1).toInstant()))
        mockMvc.perform(get("/shopify/api/subscription").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.generationLimit").value(2))
            .andExpect(jsonPath("$.generationsConsumed").value(0))
            .andExpect(jsonPath("$.allowancePeriodStart").exists()).andExpect(jsonPath("$.allowancePeriodEnd").exists())
        generate().andExpect(status().isAccepted)
        generate().andExpect(status().isAccepted)
        generate().andExpect(status().isTooManyRequests)
        assertEquals(2, count("usage_periods"))
        assertEquals(4, dsl.fetchOne("SELECT sum(generations_consumed)::int AS total FROM usage_periods WHERE billing_scope_id = ?", scope)!!.get("total", Int::class.java))
    }

    @Test
    fun `unsupported image types are rejected before storage or quota consumption`() {
        connect()
        mockMvc.perform(
            multipart("/shopify/api/models")
                .file(MockMultipartFile("image1", "one.svg", "image/svg+xml", byteArrayOf(1)))
                .file(MockMultipartFile("image2", "two.png", "image/png", byteArrayOf(2)))
                .header("Authorization", "Bearer ${token()}").header("Idempotency-Key", UUID.randomUUID()),
        )
            .andExpect(status().isBadRequest)
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
        assertEquals(0, count("jobs"))
    }

    @Test
    fun `invalid generation request identifiers and missing images return bad requests`() {
        connect()
        mockMvc.perform(
            multipart("/shopify/api/models")
                .file(MockMultipartFile("image1", "one.png", "image/png", byteArrayOf(1)))
                .file(MockMultipartFile("image2", "two.png", "image/png", byteArrayOf(2)))
                .header("Authorization", "Bearer ${token()}").header("Idempotency-Key", "invalid"),
        )
            .andExpect(status().isBadRequest)
        mockMvc.perform(
            multipart("/shopify/api/models")
                .file(MockMultipartFile("image1", "one.png", "image/png", byteArrayOf(1)))
                .header("Authorization", "Bearer ${token()}").header("Idempotency-Key", UUID.randomUUID()),
        )
            .andExpect(status().isBadRequest)
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
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
        every { deletion.deleteOutput(any()) } just Runs
        redact.execute()
        assertEquals(1, count("jobs"))
        assertEquals(1, count("platform_connections"))
        assertEquals(1, count("outbox_messages"))
        assertEquals(null, dsl.fetchOne("SELECT shop_id FROM shopify_webhook_receipts WHERE event_id = 'redact-event'")!!.get("shop_id"))
    }

    @Test
    fun `privacy cleanup deletes generated outputs and retries before removing database records`() {
        connect()
        val result = generate().andExpect(status().isAccepted).andReturn()
        val id = UUID.fromString(mapper.readTree(result.response.contentAsString).path("jobId").asText())
        val output = "https://assets.example/outputs/$id/model.glb"
        dsl.execute("UPDATE jobs SET output_glb_url = ? WHERE id = ?", output, id)
        webhook("app/uninstalled")
        webhook("shop/redact", event = "output-redaction")
        every { deletion.deleteOutput(output) } throws IllegalStateException("output deletion unavailable")
        redact.execute()
        assertEquals(1, count("jobs"))
        every { deletion.deleteOutput(output) } just Runs
        redact.execute()
        assertEquals(0, count("jobs"))
        verify(exactly = 2) { deletion.deleteOutput(output) }
    }

    @Test
    fun `privacy cleanup removes Meshy task and waits for in-progress conflicts`() {
        connect()
        val result = generate().andExpect(status().isAccepted).andReturn()
        val id = UUID.fromString(mapper.readTree(result.response.contentAsString).path("jobId").asText())
        val meshy = mockk<GenerationProviderClient>()
        every { providers.named("meshy") } returns meshy
        every { meshy.deleteTask("multi-image-to-3d:task") } throws IllegalStateException("provider still processing")
        dsl.execute("INSERT INTO generation_provider_tasks(job_id, provider, task_id, state) VALUES (?, 'meshy', 'multi-image-to-3d:task', 'submitted')", id)
        webhook("app/uninstalled")
        webhook("shop/redact")
        redact.execute()
        assertEquals(1, count("jobs"))
        every { meshy.deleteTask(any()) } just Runs
        every { retainedOutputs.delete(any()) } just Runs
        redact.execute()
        assertEquals(0, count("jobs"))
        assertEquals(0, count("generation_provider_tasks"))
        verify(exactly = 2) { meshy.deleteTask("multi-image-to-3d:task") }
        verify(exactly = 1) { retainedOutputs.delete(id) }
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
