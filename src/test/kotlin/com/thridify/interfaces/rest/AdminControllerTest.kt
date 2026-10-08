package com.thridify.interfaces.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.bearerToken
import com.thridify.interfaces.rest.dto.CreatePlanRequest
import com.thridify.interfaces.rest.dto.UpdatePlanRequest
import com.thridify.registerAndGetToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

class AdminControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var adminToken: String
    private lateinit var normalToken: String

    @BeforeEach
    fun setup() {
        resetDatabase()

        // Insert an admin user directly via SQL to get an admin token
        val adminId = UUID.randomUUID()
        dsl.execute(
            """
            INSERT INTO users (id, email, password_hash, name, is_admin, created_at)
            VALUES ('$adminId', 'admin@example.com',
                    '$2a$10${'$'}HLLKTbMvJzhF0PJL0XMiT.3a6/wPF42p0q7.MRtKvbPGFvXVOK02e',
                    'Admin', true, now())
            """.trimIndent(),
        )

        // Use the test register helper for the admin user
        // Actually, re-register via HTTP and then promote via SQL:
        adminToken = mockMvc.registerAndGetToken(objectMapper, email = "admin@test.com", password = "pass", name = "Admin")
        dsl.execute("UPDATE users SET is_admin = true WHERE email = 'admin@test.com'")
        // Re-login to get a fresh token reflecting is_admin (token payload is static, but our filter
        // reads is_admin from DB on each request, so the token doesn't need to be re-issued)

        normalToken = mockMvc.registerAndGetToken(objectMapper, email = "normal@test.com", password = "pass", name = "Normal")
    }

    // -------- GET /admin/plans --------

    @Test
    fun `admin can list all plans`() {
        seedPlans()

        mockMvc.get("/admin/plans") {
            header("Authorization", bearerToken(adminToken))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `normal user cannot access admin plans - returns 403`() {
        mockMvc.get("/admin/plans") {
            header("Authorization", bearerToken(normalToken))
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `unauthenticated request to admin returns 401`() {
        mockMvc.get("/admin/plans")
            .andExpect { status { isUnauthorized() } }
    }

    // -------- POST /admin/plans --------

    @Test
    fun `admin can create a free plan`() {
        val request = CreatePlanRequest(
            name = "starter",
            displayName = "Starter",
            description = "Starter plan",
            rateLimitRpm = 60,
            monthlyQuota = 100,
            priceCents = 0,
            currency = "usd",
            sortOrder = 1,
        )

        mockMvc.post("/admin/plans") {
            header("Authorization", bearerToken(adminToken))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("starter") }
            jsonPath("$.priceCents") { value(0) }
            jsonPath("$.isActive") { value(true) }
        }
    }

    // -------- PUT /admin/plans/{id} --------

    @Test
    fun `admin can update a plan`() {
        val planId = seedSinglePlan()

        val update = UpdatePlanRequest(
            displayName = "Updated Name",
            description = "Updated desc",
            priceCents = 1900,
            rateLimitRpm = 120,
            monthlyQuota = 500,
            sortOrder = 2,
        )

        mockMvc.put("/admin/plans/$planId") {
            header("Authorization", bearerToken(adminToken))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(update)
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Updated Name") }
            jsonPath("$.priceCents") { value(1900) }
        }
    }

    @Test
    fun `changing a price is rejected without changing the advertised plan`() {
        val planId = seedSinglePlan()
        mockMvc.put("/admin/plans/$planId") {
            header("Authorization", bearerToken(adminToken))
            contentType = MediaType.APPLICATION_JSON
            content = """{"priceCents":4900,"displayName":"Wrong price"}"""
        }.andExpect { status { isBadRequest() } }
        kotlin.test.assertEquals(1900, dsl.fetchOne("SELECT price_cents FROM plans WHERE id = ?", planId)!!.get("price_cents", Int::class.java))
        kotlin.test.assertEquals("Test Plan", dsl.fetchOne("SELECT display_name FROM plans WHERE id = ?", planId)!!.get("display_name", String::class.java))
    }

    // -------- DELETE /admin/plans/{id} --------

    @Test
    fun `admin can deactivate a plan`() {
        val planId = seedSinglePlan()

        mockMvc.delete("/admin/plans/$planId") {
            header("Authorization", bearerToken(adminToken))
        }.andExpect { status { isNoContent() } }

        // Verify it's marked inactive
        val isActive = dsl.fetchOne("SELECT is_active FROM plans WHERE id = '$planId'")
            ?.get("is_active", Boolean::class.java)
        assert(isActive == false)
    }

    @Test
    fun `deactivate non-existent plan returns 404`() {
        mockMvc.delete("/admin/plans/${UUID.randomUUID()}") {
            header("Authorization", bearerToken(adminToken))
        }.andExpect { status { isNotFound() } }
    }

    // -------- Helpers --------

    private fun seedPlans(): List<UUID> {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        dsl.execute(
            """
            INSERT INTO plans (id, name, display_name, rate_limit_rpm, monthly_quota, price_cents, currency, is_active, sort_order)
            VALUES
                ('$id1', 'plan_a', 'Plan A', 60,  100, 0,    'usd', true, 1),
                ('$id2', 'plan_b', 'Plan B', 300, 500, 2900, 'usd', true, 2)
            """.trimIndent(),
        )
        return listOf(id1, id2)
    }

    private fun seedSinglePlan(): UUID {
        val id = UUID.randomUUID()
        dsl.execute(
            """
            INSERT INTO plans (id, name, display_name, rate_limit_rpm, monthly_quota, price_cents, currency, is_active, sort_order)
            VALUES ('$id', 'testplan', 'Test Plan', 60, 100, 1900, 'usd', true, 1)
            """.trimIndent(),
        )
        return id
    }
}
