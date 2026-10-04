package com.`3dify`.interfaces.rest

import com.`3dify`.IntegrationTestBase
import com.`3dify`.bearerToken
import com.`3dify`.interfaces.rest.dto.CreateCheckoutRequest
import com.`3dify`.interfaces.rest.dto.PortalRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class SubscriptionControllerTest : IntegrationTestBase() {

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
            content = """{"email":"sub@example.com","password":"pass","name":"Sub User"}"""
        }.andReturn()
        val resp = objectMapper.readTree(result.response.contentAsString)
        token = resp.get("token").asText()
        userId = UUID.fromString(resp.get("userId").asText())
    }

    // -------- GET /dashboard/subscription --------

    @Test
    fun `get status with no subscription returns inactive response`() {
        mockMvc.get("/dashboard/subscription") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.isActive") { value(false) }
            jsonPath("$.status") { doesNotExist() }
        }
    }

    @Test
    fun `get status with active free subscription returns active`() {
        activateFreePlan()

        mockMvc.get("/dashboard/subscription") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.isActive") { value(true) }
            jsonPath("$.status") { value("active") }
            jsonPath("$.planName") { value("free") }
        }
    }

    @Test
    fun `get subscription status requires auth`() {
        mockMvc.get("/dashboard/subscription")
            .andExpect { status { isUnauthorized() } }
    }

    // -------- POST /dashboard/subscription/checkout --------

    @Test
    fun `checkout for free plan returns success url directly`() {
        val freePlanId = getFreePlanId()
        val request = CreateCheckoutRequest(
            planId = freePlanId,
            successUrl = "http://localhost:3000/subscribe/success",
            cancelUrl = "http://localhost:3000/subscribe/cancel",
        )

        mockMvc.post("/dashboard/subscription/checkout") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            // Free plans return the successUrl directly (no Stripe redirect)
            jsonPath("$.checkoutUrl") { value("http://localhost:3000/subscribe/success") }
        }

        // Verify subscription was created in DB
        val count = dsl.fetchCount(
            dsl.selectOne().from("subscriptions")
                .where("user_id = '$userId'"),
        )
        assert(count == 1)
    }

    @Test
    fun `checkout for non-existent plan returns 404`() {
        val request = CreateCheckoutRequest(
            planId = UUID.randomUUID(),
            successUrl = "http://localhost:3000/success",
            cancelUrl = "http://localhost:3000/cancel",
        )

        mockMvc.post("/dashboard/subscription/checkout") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `billing portal without subscription returns 400`() {
        val request = PortalRequest(returnUrl = "http://localhost:3000/dashboard")

        mockMvc.post("/dashboard/subscription/portal") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isBadRequest() } }
    }

    // -------- Helpers --------

    private fun seedFreePlan() {
        dsl.execute(
            """
            INSERT INTO plans (id, name, display_name, rate_limit_rpm, monthly_quota, price_cents, currency, is_active, sort_order)
            VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'free', 'Free', 60, 100, 0, 'usd', true, 1)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
    }

    private fun getFreePlanId(): UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private fun activateFreePlan() {
        dsl.execute(
            """
            INSERT INTO subscriptions (id, user_id, plan_id, status, created_at)
            VALUES (gen_random_uuid(), '$userId', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'active', now())
            """.trimIndent(),
        )
    }
}
