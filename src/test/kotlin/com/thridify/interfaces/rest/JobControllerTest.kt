package com.thridify.interfaces.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.bearerToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class JobControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var token: String
    private lateinit var userId: UUID

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedFreePlan()
        val result = mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"jobs@example.com","password":"pass","name":"Jobs User"}"""
        }.andReturn()
        val resp = objectMapper.readTree(result.response.contentAsString)
        token = resp.get("token").asText()
        userId = UUID.fromString(resp.get("userId").asText())
    }

    @Test
    fun `list jobs returns empty list when user has no jobs`() {
        mockMvc.get("/dashboard/jobs") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `list jobs returns jobs belonging to the authenticated user`() {
        val apiKeyId = seedApiKey()
        seedJob(apiKeyId, userId)

        mockMvc.get("/dashboard/jobs") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].status") { value("PENDING") }
        }
    }

    @Test
    fun `get job history returns entries for a job`() {
        val apiKeyId = seedApiKey()
        val jobId = seedJob(apiKeyId, userId)
        seedJobHistory(jobId)

        mockMvc.get("/dashboard/jobs/$jobId/history") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].status") { value("PENDING") }
            jsonPath("$[1].status") { value("PROCESSING") }
        }
    }

    @Test
    fun `list jobs requires auth`() {
        mockMvc.get("/dashboard/jobs")
            .andExpect { status { isUnauthorized() } }
    }

    // -------- helpers --------

    private fun seedFreePlan() {
        dsl.execute(
            """
            INSERT INTO plans (id, name, display_name, rate_limit_rpm, monthly_quota, price_cents, currency, is_active, sort_order)
            VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'free', 'Free', 60, 100, 0, 'usd', true, 1)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
    }

    private fun seedApiKey(): UUID {
        val id = UUID.randomUUID()
        dsl.execute(
            """
            INSERT INTO api_keys (id, workspace_id, billing_scope_id, created_by_user_id, plan_id, key_hash, key_prefix, is_active, created_at)
            VALUES ('$id', '${workspaceId(userId)}', '${scopeId(userId)}', '$userId', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                    'testhash', 'omni_pk_test', true, now())
            """.trimIndent(),
        )
        return id
    }

    private fun seedJob(apiKeyId: UUID, userId: UUID): UUID {
        val id = UUID.randomUUID()
        dsl.execute(
            """
            INSERT INTO jobs (id, workspace_id, billing_scope_id, api_key_id, status, input_image_1, input_image_2, created_at)
            VALUES ('$id', '${workspaceId(userId)}', '${scopeId(userId)}', '$apiKeyId', 'PENDING', 'inputs/a.png', 'inputs/b.png', now())
            """.trimIndent(),
        )
        return id
    }

    private fun seedJobHistory(jobId: UUID) {
        dsl.execute(
            """
            INSERT INTO job_history (id, job_id, status, created_at)
            VALUES
                (gen_random_uuid(), '$jobId', 'PENDING',    now()),
                (gen_random_uuid(), '$jobId', 'PROCESSING', now())
            """.trimIndent(),
        )
    }
}
