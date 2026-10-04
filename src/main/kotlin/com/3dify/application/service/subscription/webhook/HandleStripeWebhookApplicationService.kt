package com.`3dify`.application.service.subscription.webhook

import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.billing.BillingClient
import com.`3dify`.domain.billing.BillingEvent
import com.`3dify`.domain.subscription.SubscriptionPolicy
import com.`3dify`.domain.subscription.SubscriptionRepository
import com.`3dify`.domain.transaction.TransactionProvider
import com.`3dify`.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HandleStripeWebhookApplicationService(
    private val subscriptions: SubscriptionRepository,
    private val keys: ApiKeyRepository,
    private val billing: BillingClient,
    private val policy: SubscriptionPolicy,
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
                if (policy.shouldReactivateKeys(sub.status)) {
                    subscriptions.findByStripeSubId(sub.id)?.let {
                        keys.setActiveByUserId(it.userId, true)
                        metrics.subscriptionsActivated.increment()
                    }
                }
            }

            is BillingEvent.SubscriptionDeleted -> {
                subscriptions.updateStatusByStripeSubId(event.subscriptionId, "canceled", null)
                subscriptions.findByStripeSubId(event.subscriptionId)?.let {
                    keys.setActiveByUserId(it.userId, false)
                    metrics.subscriptionsCanceled.increment()
                    log.info("Subscription canceled -- API keys deactivated [userId={}]", it.userId)
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
