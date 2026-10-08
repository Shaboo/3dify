package com.thridify.application.service.subscription.webhook

import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.billing.BillingEvent
import com.thridify.domain.subscription.SubscriptionRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HandleStripeWebhookApplicationService(
    private val subscriptions: SubscriptionRepository,
    private val keys: ApiKeyRepository,
    private val billing: BillingClient,
    private val metrics: AppMetrics,
    private val transactions: TransactionProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: HandleStripeWebhookCommand) = transactions.transaction {
        when (val event = billing.verifyEvent(command.payload, command.signature)) {
            is BillingEvent.CheckoutCompleted -> {
                val sub = billing.retrieveSubscription(event.subscriptionId)
                subscriptions.upsertByUserId(event.userId, event.planId, event.subscriptionId, sub.customerId, sub.status, sub.periodEnd)
                keys.updatePlanForUser(event.userId, event.planId)
                metrics.subscriptionsActivated.increment()
                log.info("Subscription activated via Stripe checkout [userId={}, planId={}]", event.userId, event.planId)
            }

            is BillingEvent.SubscriptionUpdated -> {
                val sub = event.subscription
                subscriptions.updateStatusByStripeSubId(sub.id, sub.status, sub.periodEnd)
                log.info("Subscription updated [stripeSubId={}, status={}]", sub.id, sub.status)
            }

            is BillingEvent.SubscriptionDeleted -> {
                subscriptions.updateStatusByStripeSubId(event.subscriptionId, "canceled", null)
                subscriptions.findByStripeSubId(event.subscriptionId)?.let {
                    metrics.subscriptionsCanceled.increment()
                    log.info("Subscription canceled [userId={}]", it.userId)
                }
            }

            is BillingEvent.PaymentFailed -> {
                subscriptions.updateStatusByStripeCustomerId(event.customerId, "past_due")
                metrics.subscriptionsPastDue.increment()
                log.warn("Payment failed -- subscription marked past_due [customerId={}]", event.customerId)
            }

            BillingEvent.Ignored -> Unit
        }
    }
}
