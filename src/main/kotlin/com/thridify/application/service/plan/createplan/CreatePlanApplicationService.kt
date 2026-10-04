package com.thridify.application.service.plan.createplan

import com.thridify.application.service.plan.toResult
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.plan.PlanPolicy
import com.thridify.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class CreatePlanApplicationService(private val plans: PlanRepository, private val billing: BillingClient, private val policy: PlanPolicy) {

    fun execute(command: CreatePlanCommand) = with(command) {
        val priceId = if (policy.requiresBillingPrice(priceCents)) billing.createPrice(displayName, priceCents, currency) else null
        val id = plans.insert(name, displayName, description, rateLimitRpm, monthlyQuota, priceCents, currency, priceId, sortOrder)
        (plans.findById(id) ?: throw IllegalStateException("Failed to retrieve created plan")).toResult()
    }
}
