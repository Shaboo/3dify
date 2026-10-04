package com.thridify.interfaces.rest

import com.thridify.IntegrationTestBase
import com.thridify.infrastructure.security.RateLimiterService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class GenerateControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var rateLimiterService: RateLimiterService

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
    }

    private fun createTestUserAndApiKey(planName: String = "free"): String {
        val userId = UUID.randomUUID()
        dsl.execute("INSERT INTO users (id, email, password_hash) VALUES ('$userId', 'test@example.com', 'dummy_hash')")

        // Create subscription to the specified plan
        dsl.execute(
            """
            INSERT INTO subscriptions (id, user_id, plan_id, status)
            SELECT gen_random_uuid(), '$userId', id, 'active'
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
            INSERT INTO api_keys (id, user_id, plan_id, key_hash, key_prefix, label, is_active)
            VALUES ('$apiKeyId', '$userId', $planIdQuery, '$hash', 'omni_pk_', 'Test Key', true)
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
