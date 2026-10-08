package com.thridify.application.service.subscription

import com.thridify.application.service.subscription.webhook.HandleStripeWebhookApplicationService
import com.thridify.application.service.subscription.webhook.HandleStripeWebhookCommand
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.billing.BillingEvent
import com.thridify.domain.billing.BillingSubscription
import com.thridify.domain.billing.StripeEventRepository
import com.thridify.domain.billing.VerifiedBillingEvent
import com.thridify.domain.subscription.SubscriptionEntity
import com.thridify.domain.subscription.SubscriptionPolicy
import com.thridify.domain.subscription.SubscriptionRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
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
    private val events: StripeEventRepository = mockk {
        every { latest(any()) } returns null
        every { record(any(), any()) } returns true
    }
    private val service = HandleStripeWebhookApplicationService(subscriptions, keys, billing, metrics, transactions, events, SubscriptionPolicy())
    private fun handle(event: BillingEvent) {
        every { billing.verifyEvent("payload", "signature") } returns VerifiedBillingEvent("event", OffsetDateTime.now(), event)
        service.execute(HandleStripeWebhookCommand("payload", "signature"))
    }

    @Test
    fun `completed checkout retrieves billing state then activates subscription and key plan`() {
        val user = UUID.randomUUID()
        val plan = UUID.randomUUID()
        val end = OffsetDateTime.now()
        every { billing.retrieveSubscription("sub-1") } returns BillingSubscription("sub-1", "customer-1", "trialing", end)
        every { subscriptions.findActiveByUserId(user) } returns null
        handle(BillingEvent.CheckoutCompleted(user, plan, "sub-1"))
        verifyOrder {
            billing.retrieveSubscription("sub-1")
            subscriptions.upsertByUserId(user, plan, "sub-1", "customer-1", "trialing", end)
            keys.updatePlanForUser(user, plan)
        }
        assertEquals(1.0, metrics.subscriptionsActivated.count())
    }

    private val user = UUID.randomUUID()
    private val stored = SubscriptionEntity(UUID.randomUUID(), user, UUID.randomUUID(), "sub-1", "customer-1", "active", null, OffsetDateTime.now(), null)

    @Test
    fun `stale failure reads current provider state and never revives keys`() {
        every { subscriptions.findByStripeCustomerId("customer-1") } returns stored
        every { billing.retrieveSubscription("sub-1") } returns BillingSubscription("sub-1", "customer-1", "active", null)
        handle(BillingEvent.PaymentFailed("customer-1"))
        verify { subscriptions.updateStatusByStripeSubId("sub-1", "active", null) }
        verify { keys wasNot Called }
    }

    @Test
    fun `duplicate events do not change subscriptions`() {
        every { subscriptions.findByStripeSubId("sub-1") } returns stored
        every { events.record(any(), any()) } returns false
        handle(BillingEvent.SubscriptionDeleted("sub-1"))
        verify(exactly = 0) { subscriptions.updateStatusByStripeSubId(any(), any(), any()) }
    }

    @Test
    fun `older events do not change subscriptions`() {
        every { subscriptions.findByStripeSubId("sub-1") } returns stored
        every { events.latest(user) } returns OffsetDateTime.now().plusDays(1)
        handle(BillingEvent.SubscriptionDeleted("sub-1"))
        verify(exactly = 0) { subscriptions.updateStatusByStripeSubId(any(), any(), any()) }
    }

    @Test
    fun `deletion cancels subscription without revoking keys`() {
        every { subscriptions.findByStripeSubId("sub-1") } returns stored
        handle(BillingEvent.SubscriptionDeleted("sub-1"))
        verify { subscriptions.updateStatusByStripeSubId("sub-1", "canceled", null) }
        verify { keys wasNot Called }
    }
}
