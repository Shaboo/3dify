package com.`3dify`.application.service.subscription

import com.`3dify`.application.service.subscription.webhook.HandleStripeWebhookApplicationService
import com.`3dify`.application.service.subscription.webhook.HandleStripeWebhookCommand
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.billing.BillingClient
import com.`3dify`.domain.billing.BillingEvent
import com.`3dify`.domain.billing.BillingSubscription
import com.`3dify`.domain.subscription.SubscriptionEntity
import com.`3dify`.domain.subscription.SubscriptionPolicy
import com.`3dify`.domain.subscription.SubscriptionRepository
import com.`3dify`.domain.transaction.TransactionProvider
import com.`3dify`.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

class HandleStripeWebhookTest {
    private val subscriptions: SubscriptionRepository = mockk(relaxed = true)
    private val keys: ApiKeyRepository = mockk(relaxed = true)
    private val billing: BillingClient = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val transactions = object : TransactionProvider {
        override fun <T> transaction(action: () -> T) = action()
    }
    private val service = HandleStripeWebhookApplicationService(subscriptions, keys, billing, SubscriptionPolicy(), metrics, transactions)
    private fun handle(event: BillingEvent) {
        every { billing.verifyEvent("payload", "signature") } returns event
        service.execute(HandleStripeWebhookCommand("payload", "signature"))
    }

    @Test
    fun `completed checkout retrieves billing state then activates subscription and key plan`() {
        val user = UUID.randomUUID()
        val plan = UUID.randomUUID()
        val end = OffsetDateTime.now()
        every { billing.retrieveSubscription("sub-1") } returns BillingSubscription("sub-1", "customer-1", "trialing", end)
        handle(BillingEvent.CheckoutCompleted(user, plan, "sub-1"))
        verifyOrder {
            billing.retrieveSubscription("sub-1")
            subscriptions.upsertByUserId(user, plan, "sub-1", "customer-1", "trialing", end)
            keys.updatePlanForUser(user, plan)
        }
        assertEquals(1.0, metrics.subscriptionsActivated.count())
    }

    @Test
    fun `only active subscription updates reactivate api keys`() {
        handle(BillingEvent.SubscriptionUpdated(BillingSubscription("sub-1", "customer-1", "trialing", null)))
        verify { subscriptions.updateStatusByStripeSubId("sub-1", "trialing", null) }
        verify(exactly = 0) { keys.setActiveByUserId(any(), any()) }
        val user = UUID.randomUUID()
        val stored = SubscriptionEntity(UUID.randomUUID(), user, UUID.randomUUID(), "sub-1", "customer-1", "active", null, OffsetDateTime.now(), null)
        every { subscriptions.findByStripeSubId("sub-1") } returns stored
        handle(BillingEvent.SubscriptionUpdated(BillingSubscription("sub-1", "customer-1", "active", null)))
        verify(exactly = 1) { keys.setActiveByUserId(user, true) }
    }

    @Test
    fun `deletion cancels subscription and deactivates keys`() {
        val user = UUID.randomUUID()
        every { subscriptions.findByStripeSubId("sub-1") } returns SubscriptionEntity(UUID.randomUUID(), user, UUID.randomUUID(), "sub-1", "customer-1", "canceled", null, OffsetDateTime.now(), null)
        handle(BillingEvent.SubscriptionDeleted("sub-1"))
        verifyOrder {
            subscriptions.updateStatusByStripeSubId("sub-1", "canceled", null)
            subscriptions.findByStripeSubId("sub-1")
            keys.setActiveByUserId(user, false)
        }
        assertEquals(1.0, metrics.subscriptionsCanceled.count())
    }

    @Test
    fun `payment failure changes subscription status without deactivating keys`() {
        handle(BillingEvent.PaymentFailed("customer-1"))
        verify { subscriptions.updateStatusByStripeCustomerId("customer-1", "past_due") }
        verify(exactly = 0) { keys.setActiveByUserId(any(), any()) }
        assertEquals(1.0, metrics.subscriptionsPastDue.count())
    }

    @Test
    fun `unknown event has no subscription or key side effects`() {
        handle(BillingEvent.Ignored)
        verify { subscriptions wasNot Called }
        verify { keys wasNot Called }
    }
}
