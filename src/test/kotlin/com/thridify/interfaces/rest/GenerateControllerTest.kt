package com.thridify.interfaces.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.bearerToken
import com.thridify.infrastructure.security.RateLimiterService
import com.thridify.registerAndGetToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class GenerateControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var rateLimiterService: RateLimiterService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
    }

    private fun createTestUserAndApiKey(planName: String = "free"): String {
        val userId = UUID.randomUUID()
        dsl.execute("INSERT INTO users (id, email, password_hash) VALUES ('$userId', 'test@example.com', 'dummy_hash')")
        createDirectWorkspace(userId)

        // Create subscription to the specified plan
        dsl.execute(
            """
            INSERT INTO subscriptions (id, billing_scope_id, provider, plan_id, status)
            SELECT gen_random_uuid(), '${scopeId(userId)}', 'internal', id, 'active'
            FROM plans WHERE name = '$planName'
            """.trimIndent(),
        )

        // Create API key
        val rawKey = "omni_pk_${UUID.randomUUID()}"
        val apiKeyId = UUID.randomUUID()
        val planIdQuery = "(SELECT id FROM plans WHERE name = '$planName')"

        // Hash it using SHA-256 just like ApiKeyService does
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        dsl.execute(
            """
            INSERT INTO api_keys (id, workspace_id, billing_scope_id, created_by_user_id, plan_id, key_hash, key_prefix, label, is_active)
            VALUES ('$apiKeyId', '${workspaceId(userId)}', '${scopeId(userId)}', '$userId', $planIdQuery, '$hash', 'omni_pk_', 'Test Key', true)
            """.trimIndent(),
        )

        return rawKey
    }

    @Test
    fun `generate 3D model returns 202 Accepted`() {
        val apiKey = createTestUserAndApiKey()

        val image1 = MockMultipartFile("image1", "test1.png", "image/png", "dummy1".toByteArray())
        val image2 = MockMultipartFile("image2", "test2.png", "image/png", "dummy2".toByteArray())

        mockMvc.perform(
            multipart("/api/v1/generate")
                .file(image1)
                .file(image2)
                .header("X-API-KEY", apiKey),
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.jobId").exists())
    }

    @Test
    fun `website repeated images multipart preserves one and four photo inputs`() {
        val apiKey = createTestUserAndApiKey()
        for (count in listOf(1, 4)) {
            val request = multipart("/api/v1/generate").header("X-API-KEY", apiKey)
            repeat(count) { index ->
                request.file(MockMultipartFile("images", "angle-$index.png", "image/png", "photo-$index".toByteArray()))
            }
            mockMvc.perform(request)
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.jobId").exists())
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/jobs").header("X-API-KEY", apiKey))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
        val photoCounts = dsl.fetch("SELECT cardinality(input_images) AS count FROM jobs").map { it.get("count", Int::class.java) }.sorted()
        kotlin.test.assertEquals(listOf(1, 4), photoCounts)
    }

    @Test
    fun `website user can activate free plan create key submit photos and see owned job without Stripe`() {
        val token = mockMvc.registerAndGetToken(objectMapper, email = "website-generation@example.com")
        val planId = dsl.fetchOne("SELECT id FROM plans WHERE name = 'free'")!!.get("id").toString()
        mockMvc.post("/dashboard/subscription/checkout") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("planId" to planId, "successUrl" to "http://localhost:3000/dashboard", "cancelUrl" to "http://localhost:3000/dashboard"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.checkoutUrl") { value("http://localhost:3000/dashboard") }
        }
        val created = mockMvc.post("/dashboard/api-keys") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = """{"label":"Website generation","planName":"free"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val key = objectMapper.readTree(created.response.contentAsString).get("key").asText()
        mockMvc.perform(multipart("/api/v1/generate").file(MockMultipartFile("images", "front.png", "image/png", "photo".toByteArray())).header("X-API-KEY", key))
            .andExpect(status().isAccepted)
        mockMvc.get("/dashboard/jobs") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].inputImages.length()") { value(1) }
            jsonPath("$[0].status") { value("PENDING") }
        }
        mockMvc.get("/admin/plans") { header("Authorization", bearerToken(token)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `generate fails without Authorization header`() {
        val image1 = MockMultipartFile("image1", "test1.png", "image/png", "dummy1".toByteArray())
        val image2 = MockMultipartFile("image2", "test2.png", "image/png", "dummy2".toByteArray())

        mockMvc.perform(
            multipart("/api/v1/generate")
                .file(image1)
                .file(image2),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `generate enforces rate limits and returns 429`() {
        val apiKey = createTestUserAndApiKey("free") // Free plan is 60 req/min

        val image1 = MockMultipartFile("image1", "test1.png", "image/png", "dummy1".toByteArray())
        val image2 = MockMultipartFile("image2", "test2.png", "image/png", "dummy2".toByteArray())

        // Exhaust the rate limit (60 requests)
        for (i in 1..60) {
            mockMvc.perform(
                multipart("/api/v1/generate")
                    .file(image1)
                    .file(image2)
                    .header("X-API-KEY", apiKey),
            ).andExpect(status().isAccepted)
        }

        // The 61st request should be rejected
        mockMvc.perform(
            multipart("/api/v1/generate")
                .file(image1)
                .file(image2)
                .header("X-API-KEY", apiKey),
        ).andExpect(status().isTooManyRequests)
    }
}
