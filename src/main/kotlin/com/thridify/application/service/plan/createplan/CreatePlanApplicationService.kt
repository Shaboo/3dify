package com.thridify.application.service.plan.createplan

import com.thridify.application.service.plan.toResult
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.billing.BillingCommandPolicy
import com.thridify.domain.billing.BillingCommandRepository
import com.thridify.domain.plan.PlanPolicy
import com.thridify.domain.plan.PlanRepository
import com.thridify.domain.transaction.TransactionProvider
import org.springframework.stereotype.Service

@Service
class CreatePlanApplicationService(private val plans: PlanRepository, private val billing: BillingClient, private val policy: PlanPolicy, private val commands: BillingCommandRepository, private val commandPolicy: BillingCommandPolicy, private val transactions: TransactionProvider) {

    fun execute(command: CreatePlanCommand) = with(command) {
        policy.ensureValidCreation(name, displayName, rateLimitRpm, monthlyQuota, priceCents, currency)
        val key = "plan:$requestId"
        val fingerprint = commandPolicy.fingerprint(name, displayName, description, rateLimitRpm.toString(), monthlyQuota.toString(), priceCents.toString(), currency, sortOrder.toString())
        val record = transactions.transaction {
            commands.reserve(key, fingerprint)
            commands.lock(key).also {
                commandPolicy.ensureSameRequest(it, fingerprint)
                if (it.result == null) policy.ensureNameAvailable(plans.findByName(name))
            }
        }
        record.result?.let { return@with policy.requirePlan(plans.findById(java.util.UUID.fromString(it)), java.util.UUID.fromString(it)).toResult() }
        val priceId = if (policy.requiresBillingPrice(priceCents)) {
            record.providerResult ?: run {
                commandPolicy.ensureProviderRetrySafe(record)
                val created = billing.createPrice(displayName, priceCents, currency, key)
                transactions.transaction { commands.recordProviderResult(key, created) }
                created
            }
        } else {
            null
        }
        transactions.transaction {
            val latest = commands.lock(key)
            val id = latest.result?.let(java.util.UUID::fromString) ?: plans.insert(name, displayName, description, rateLimitRpm, monthlyQuota, priceCents, currency, priceId, sortOrder).also { commands.complete(key, it.toString()) }
            policy.requirePlan(plans.findById(id), id).toResult()
        }
    }
}
