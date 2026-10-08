package com.thridify.application.service.subscription.checkout

import com.thridify.application.service.subscription.checkout.CheckoutResult
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.billing.BillingCommandPolicy
import com.thridify.domain.billing.BillingCommandRepository
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
    private val commands: BillingCommandRepository,
    private val commandPolicy: BillingCommandPolicy,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: CreateCheckoutSessionCommand): CheckoutResult {
        log.info("Creating checkout session [userId={}, planId={}]", command.userId, command.planId)
        return when (val decision = policy.checkout(plans.findById(command.planId), command.planId)) {
            CheckoutDecision.ActivateFree -> transactions.transaction {
                subscriptions.lock(command.userId)
                policy.ensureFreeActivation(subscriptions.findActiveByUserId(command.userId))
                subscriptions.upsertByUserId(command.userId, command.planId, null, null, "active", null)
                keys.updatePlanForUser(command.userId, command.planId)
                metrics.subscriptionsActivated.increment()
                CheckoutResult(command.successUrl)
            }

            is CheckoutDecision.Paid -> {
                val key = "checkout:${command.userId}:${command.requestId}"
                val fingerprint = commandPolicy.fingerprint(command.planId.toString(), command.successUrl, command.cancelUrl)
                val record = transactions.transaction { commands.reserve(key, fingerprint) }
                commandPolicy.ensureSameRequest(record, fingerprint)
                record.result?.let { return CheckoutResult(it) }
                commandPolicy.ensureProviderRetrySafe(record)
                val url = record.providerResult ?: billing.createCheckout(command.userId, command.planId, decision.priceId, command.successUrl, command.cancelUrl, key)
                transactions.transaction {
                    commands.recordProviderResult(key, url)
                    commands.complete(key, url)
                }
                CheckoutResult(url)
            }
        }
    }
}
