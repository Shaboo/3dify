package com.thridify.application.service.subscription.webhook
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.billing.BillingEvent
import com.thridify.domain.billing.StripeEventRepository
import com.thridify.domain.subscription.SubscriptionPolicy
import com.thridify.domain.subscription.SubscriptionRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.springframework.stereotype.Service
@Service
class HandleStripeWebhookApplicationService(
    private val subscriptions: SubscriptionRepository,
    private val keys: ApiKeyRepository,
    private val billing: BillingClient,
    private val metrics: AppMetrics,
    private val transactions: TransactionProvider,
    private val events: StripeEventRepository,
    private val policy: SubscriptionPolicy,
) {
    fun execute(command: HandleStripeWebhookCommand) {
        val verified = billing.verifyEvent(command.payload, command.signature)
        val event = verified.event
        val owner = when (event) {
            is BillingEvent.CheckoutCompleted -> event.userId
            is BillingEvent.SubscriptionUpdated -> subscriptions.findByStripeSubId(event.subscription.id)?.userId
            is BillingEvent.SubscriptionDeleted -> subscriptions.findByStripeSubId(event.subscriptionId)?.userId
            is BillingEvent.PaymentFailed -> subscriptions.findByStripeCustomerId(event.customerId)?.userId
            BillingEvent.Ignored -> null
        } ?: return
        val current = when (event) {
            is BillingEvent.CheckoutCompleted -> billing.retrieveSubscription(event.subscriptionId)
            is BillingEvent.SubscriptionUpdated -> billing.retrieveSubscription(event.subscription.id)
            is BillingEvent.PaymentFailed -> subscriptions.findByStripeCustomerId(event.customerId)?.stripeSubscriptionId?.let(billing::retrieveSubscription)
            else -> null
        }
        transactions.transaction {
            subscriptions.lock(owner)
            val latest = events.latest(owner)
            if (!events.record(owner, verified) || !policy.currentEvent(verified.createdAt, latest)) return@transaction
            when (event) {
                is BillingEvent.CheckoutCompleted -> {
                    val sub = requireNotNull(current)
                    if (!policy.activateCheckout(subscriptions.findActiveByUserId(owner), sub.id, sub.status)) return@transaction
                    subscriptions.upsertByUserId(owner, event.planId, sub.id, sub.customerId, sub.status, sub.periodEnd)
                    keys.updatePlanForUser(owner, event.planId)
                    metrics.subscriptionsActivated.increment()
                }

                is BillingEvent.SubscriptionUpdated, is BillingEvent.PaymentFailed -> current?.let {
                    subscriptions.updateStatusByStripeSubId(it.id, it.status, it.periodEnd)
                }

                is BillingEvent.SubscriptionDeleted -> {
                    subscriptions.updateStatusByStripeSubId(event.subscriptionId, "canceled", null)
                    metrics.subscriptionsCanceled.increment()
                }

                BillingEvent.Ignored -> Unit
            }
        }
    }
}
