package com.omni3d.api.service

import com.omni3d.api.domain.PlanEntity
import com.omni3d.api.domain.SubscriptionWithPlanEntity
import com.omni3d.api.exception.NotFoundException
import com.omni3d.api.metrics.AppMetrics
import com.omni3d.api.repository.ApiKeyRepository
import com.omni3d.api.repository.PlanRepository
import com.omni3d.api.repository.SubscriptionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionServiceTest {

    private val subscriptionRepository: SubscriptionRepository = mockk()
    private val planRepository: PlanRepository = mockk()
    private val apiKeyRepository: ApiKeyRepository = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val service = SubscriptionService(
        subscriptionRepository, planRepository, apiKeyRepository, metrics,
        webhookSecret = "whsec_test_stub"
    )

    private fun subWithPlan(
        status: String = "active",
        planId: UUID = UUID.randomUUID(),
        planName: String = "pro",
        planDisplayName: String = "Pro",
        planPriceCents: Int = 2900,
        stripeCustomerId: String? = null
    ) = SubscriptionWithPlanEntity(
        id                   = UUID.randomUUID(),
        userId               = UUID.randomUUID(),
        planId               = planId,
        stripeSubscriptionId = null,
        stripeCustomerId     = stripeCustomerId,
        status               = status,
        currentPeriodEnd     = null,
        planName             = planName,
        planDisplayName      = planDisplayName,
        planRateLimitRpm     = 60,
        planMonthlyQuota     = 100,
        planPriceCents       = planPriceCents
    )

    private fun plan(
        id: UUID = UUID.randomUUID(),
        priceCents: Int = 0,
        stripePriceId: String? = null
    ) = PlanEntity(
        id            = id,
        name          = "free",
        displayName   = "Free",
        description   = null,
        rateLimitRpm  = 60,
        monthlyQuota  = 100,
        priceCents    = priceCents,
        currency      = "usd",
        stripePriceId = stripePriceId,
        isActive      = true,
        sortOrder     = 0
    )

    // -------- getStatus --------

    @Test
    fun `getStatus returns inactive response when no subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns null

        val status = service.getStatus(UUID.randomUUID())

        assertFalse(status.isActive)
        assertEquals(null, status.status)
        assertEquals(null, status.planName)
    }

    @Test
    fun `getStatus returns active response for active subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns subWithPlan(
            status = "active", planName = "pro", planPriceCents = 2900
        )

        val status = service.getStatus(UUID.randomUUID())

        assertTrue(status.isActive)
        assertEquals("active", status.status)
        assertEquals("pro", status.planName)
        assertEquals(2900, status.priceCents)
    }

    @Test
    fun `getStatus isActive is false for past_due subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns subWithPlan(status = "past_due")

        val status = service.getStatus(UUID.randomUUID())

        assertFalse(status.isActive)
        assertEquals("past_due", status.status)
    }

    // -------- createCheckoutSession (free plan) --------

    @Test
    fun `createCheckoutSession for free plan upserts subscription and returns successUrl`() {
        val userId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        every { planRepository.findById(planId) } returns plan(id = planId, priceCents = 0)
        every { subscriptionRepository.upsertByUserId(userId, planId, null, null, "active", null) } just Runs
        every { apiKeyRepository.updatePlanForUser(userId, planId) } just Runs

        val response = service.createCheckoutSession(
            userId, planId,
            successUrl = "http://localhost:3000/success",
            cancelUrl  = "http://localhost:3000/cancel"
        )

        assertEquals("http://localhost:3000/success", response.checkoutUrl)
        verify(exactly = 1) { subscriptionRepository.upsertByUserId(userId, planId, null, null, "active", null) }
    }

    @Test
    fun `createCheckoutSession throws NotFoundException when plan not found`() {
        every { planRepository.findById(any()) } returns null

        assertThrows<NotFoundException> {
            service.createCheckoutSession(UUID.randomUUID(), UUID.randomUUID(), "http://s", "http://c")
        }
    }

    // -------- createBillingPortal --------

    @Test
    fun `createBillingPortal throws when no active subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns null

        assertThrows<com.omni3d.api.exception.ApiException> {
            service.createBillingPortal(UUID.randomUUID(), "http://return")
        }
    }

    @Test
    fun `createBillingPortal throws when no Stripe customer ID on subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns subWithPlan(stripeCustomerId = null)

        assertThrows<com.omni3d.api.exception.ApiException> {
            service.createBillingPortal(UUID.randomUUID(), "http://return")
        }
    }
}
