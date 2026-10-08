package com.thridify.application.service.subscription.checkout

import com.thridify.application.service.subscription.checkout.CheckoutResult
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.plan.PlanRepository
import com.thridify.domain.subscription.CheckoutDecision
import com.thridify.domain.subscription.SubscriptionPolicy
import com.thridify.domain.subscription.SubscriptionRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreateCheckoutSessionApplicationService(
    private val subscriptions: SubscriptionRepository,
    private val plans: PlanRepository,
    private val keys: ApiKeyRepository,
    private val metrics: AppMetrics,
    private val billing: BillingClient,
    private val policy: SubscriptionPolicy,
    private val transactions: TransactionProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: CreateCheckoutSessionCommand): CheckoutResult = transactions.transaction {
        log.info("Creating checkout session [userId={}, planId={}]", command.userId, command.planId)
        when (val decision = policy.checkout(plans.findById(command.planId), command.planId)) {
            CheckoutDecision.ActivateFree -> {
                subscriptions.lock(command.userId)
                policy.ensureFreeActivation(subscriptions.findActiveByUserId(command.userId))
                subscriptions.upsertByUserId(command.userId, command.planId, null, null, "active", null)
                keys.updatePlanForUser(command.userId, command.planId)
                metrics.subscriptionsActivated.increment()
                CheckoutResult(command.successUrl)
            }

            is CheckoutDecision.Paid -> {
                // Preserve the previous transaction timing; compensation is a separate behavior change.
                CheckoutResult(billing.createCheckout(command.userId, command.planId, decision.priceId, command.successUrl, command.cancelUrl))
            }
        }
    }
}
