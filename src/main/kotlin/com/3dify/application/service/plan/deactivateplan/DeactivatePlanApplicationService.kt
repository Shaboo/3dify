package com.`3dify`.application.service.plan.deactivateplan

import com.`3dify`.domain.plan.PlanPolicy
import com.`3dify`.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class DeactivatePlanApplicationService(private val plans: PlanRepository, private val policy: PlanPolicy) {

    fun execute(command: DeactivatePlanCommand) {
        policy.requirePlan(plans.findById(command.id), command.id)
        plans.deactivate(command.id)
    }
}
