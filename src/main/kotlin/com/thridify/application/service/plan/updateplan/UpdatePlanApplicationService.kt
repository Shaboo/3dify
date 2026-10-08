package com.thridify.application.service.plan.updateplan

import com.thridify.application.service.plan.toResult
import com.thridify.domain.plan.PlanPolicy
import com.thridify.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class UpdatePlanApplicationService(private val plans: PlanRepository, private val policy: PlanPolicy) {

    fun execute(command: UpdatePlanCommand) = with(command) {
        policy.ensurePriceUnchanged(policy.requirePlan(plans.findById(id), id), priceCents)
        plans.update(id, displayName, description, priceCents, rateLimitRpm, monthlyQuota, null, sortOrder)
        (plans.findById(id) ?: throw IllegalStateException("Plan not found after update")).toResult()
    }
}
