package com.`3dify`.application.service.subscription.checkout

import com.`3dify`.application.service.subscription.checkout.CheckoutResult
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.billing.BillingClient
import com.`3dify`.domain.plan.PlanRepository
import com.`3dify`.domain.subscription.CheckoutDecision
import com.`3dify`.domain.subscription.SubscriptionPolicy
import com.`3dify`.domain.subscription.SubscriptionRepository
import com.`3dify`.domain.transaction.TransactionProvider
import com.`3dify`.shared.metrics.AppMetrics
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
