package com.`3dify`.application.service

import com.`3dify`.application.service.subscription.checkout.CreateCheckoutSessionApplicationService
import com.`3dify`.application.service.subscription.checkout.CreateCheckoutSessionCommand
import com.`3dify`.application.service.subscription.portal.CreateBillingPortalApplicationService
import com.`3dify`.application.service.subscription.portal.CreateBillingPortalCommand
import com.`3dify`.application.service.subscription.status.GetSubscriptionStatusApplicationService
import com.`3dify`.application.service.subscription.status.GetSubscriptionStatusQuery
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.plan.PlanEntity
import com.`3dify`.domain.plan.PlanRepository
import com.`3dify`.domain.subscription.SubscriptionRepository
import com.`3dify`.domain.subscription.SubscriptionWithPlanEntity
import com.`3dify`.shared.exception.NotFoundException
import com.`3dify`.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
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
    private val policy = com.`3dify`.domain.subscription.SubscriptionPolicy()
    private val billing: com.`3dify`.domain.billing.BillingClient = mockk()
    private val transactions = object : com.`3dify`.domain.transaction.TransactionProvider {
        override fun <T> transaction(action: () -> T): T = action()
    }
    private val status = GetSubscriptionStatusApplicationService(subscriptionRepository, policy)
    private val checkout = CreateCheckoutSessionApplicationService(subscriptionRepository, planRepository, apiKeyRepository, metrics, billing, policy, transactions)
    private val portal = CreateBillingPortalApplicationService(subscriptionRepository, billing, policy)

    private fun subWithPlan(
        status: String = "active",
        planId: UUID = UUID.randomUUID(),
        planName: String = "pro",
        planDisplayName: String = "Pro",
        planPriceCents: Int = 2900,
        stripeCustomerId: String? = null,
    ) = SubscriptionWithPlanEntity(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        planId = planId,
        stripeSubscriptionId = null,
        stripeCustomerId = stripeCustomerId,
        status = status,
        currentPeriodEnd = null,
        planName = planName,
        planDisplayName = planDisplayName,
        planRateLimitRpm = 60,
        planMonthlyQuota = 100,
        planPriceCents = planPriceCents,
    )

    private fun plan(
        id: UUID = UUID.randomUUID(),
        priceCents: Int = 0,
        stripePriceId: String? = null,
    ) = PlanEntity(
        id = id,
        name = "free",
        displayName = "Free",
        description = null,
        rateLimitRpm = 60,
        monthlyQuota = 100,
        priceCents = priceCents,
        currency = "usd",
        stripePriceId = stripePriceId,
        isActive = true,
        sortOrder = 0,
    )

    // -------- getStatus --------

    @Test
    fun `getStatus returns inactive response when no subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns null

        val status = status.execute(GetSubscriptionStatusQuery(UUID.randomUUID()))

        assertFalse(status.isActive)
        assertEquals(null, status.status)
        assertEquals(null, status.planName)
    }

    @Test
    fun `getStatus returns active response for active subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns subWithPlan(
            status = "active",
            planName = "pro",
            planPriceCents = 2900,
        )

        val status = status.execute(GetSubscriptionStatusQuery(UUID.randomUUID()))

        assertTrue(status.isActive)
        assertEquals("active", status.status)
        assertEquals("pro", status.planName)
        assertEquals(2900, status.priceCents)
    }

    @Test
    fun `getStatus isActive is false for past_due subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns subWithPlan(status = "past_due")

        val status = status.execute(GetSubscriptionStatusQuery(UUID.randomUUID()))

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

        val response = checkout.execute(
            CreateCheckoutSessionCommand(
                userId,
                planId,
                successUrl = "http://localhost:3000/success",
                cancelUrl = "http://localhost:3000/cancel",
            ),
        )

        assertEquals("http://localhost:3000/success", response.checkoutUrl)
        verify(exactly = 1) { subscriptionRepository.upsertByUserId(userId, planId, null, null, "active", null) }
    }

    @Test
    fun `createCheckoutSession throws NotFoundException when plan not found`() {
        every { planRepository.findById(any()) } returns null

        assertThrows<NotFoundException> {
            checkout.execute(CreateCheckoutSessionCommand(UUID.randomUUID(), UUID.randomUUID(), "http://s", "http://c"))
        }
    }

    // -------- createBillingPortal --------

    @Test
    fun `paid checkout passes the selected price and redirect urls without changing subscriptions`() {
        val userId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        every { planRepository.findById(planId) } returns plan(planId, 2900, "price-1")
        every { billing.createCheckout(userId, planId, "price-1", "http://success", "http://cancel") } returns "http://checkout"
        val result = checkout.execute(CreateCheckoutSessionCommand(userId, planId, "http://success", "http://cancel"))
        assertEquals("http://checkout", result.checkoutUrl)
        verify { subscriptionRepository wasNot Called }
        verify { apiKeyRepository wasNot Called }
    }

    @Test
    fun `paid checkout rejects a plan without a linked stripe price before calling billing`() {
        val planId = UUID.randomUUID()
        every { planRepository.findById(planId) } returns plan(planId, 2900)
        val failure = assertThrows<com.`3dify`.shared.exception.BadRequestException> {
            checkout.execute(CreateCheckoutSessionCommand(UUID.randomUUID(), planId, "http://success", "http://cancel"))
        }
        assertEquals("Plan is not linked to a Stripe price", failure.message)
        verify { billing wasNot Called }
    }

    @Test
    fun `billing portal uses the linked customer and requested return url`() {
        val userId = UUID.randomUUID()
        every { subscriptionRepository.findActiveByUserId(userId) } returns subWithPlan(stripeCustomerId = "customer-1")
        every { billing.createPortal("customer-1", "http://return") } returns "http://portal"
        assertEquals("http://portal", portal.execute(CreateBillingPortalCommand(userId, "http://return")).portalUrl)
    }

    @Test
    fun `createBillingPortal throws when no active subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns null

        assertThrows<com.`3dify`.shared.exception.ApiException> {
            portal.execute(CreateBillingPortalCommand(UUID.randomUUID(), "http://return"))
        }
    }

    @Test
    fun `createBillingPortal throws when no Stripe customer ID on subscription`() {
        every { subscriptionRepository.findActiveByUserId(any()) } returns subWithPlan(stripeCustomerId = null)

        assertThrows<com.`3dify`.shared.exception.ApiException> {
            portal.execute(CreateBillingPortalCommand(UUID.randomUUID(), "http://return"))
        }
    }
}
