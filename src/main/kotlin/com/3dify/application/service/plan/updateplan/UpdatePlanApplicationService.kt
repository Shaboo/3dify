package com.`3dify`.application.service.plan.updateplan

import com.`3dify`.application.service.plan.toResult
import com.`3dify`.domain.plan.PlanPolicy
import com.`3dify`.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class UpdatePlanApplicationService(private val plans: PlanRepository, private val policy: PlanPolicy) {

    fun execute(command: UpdatePlanCommand) = with(command) {
        policy.requirePlan(plans.findById(id), id)
        plans.update(id, displayName, description, priceCents, rateLimitRpm, monthlyQuota, null, sortOrder)
        (plans.findById(id) ?: throw IllegalStateException("Plan not found after update")).toResult()
    }
}
