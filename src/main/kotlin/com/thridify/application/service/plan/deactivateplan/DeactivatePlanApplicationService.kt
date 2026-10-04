package com.thridify.application.service.plan.deactivateplan

import com.thridify.domain.plan.PlanPolicy
import com.thridify.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class DeactivatePlanApplicationService(private val plans: PlanRepository, private val policy: PlanPolicy) {

    fun execute(command: DeactivatePlanCommand) {
        policy.requirePlan(plans.findById(command.id), command.id)
        plans.deactivate(command.id)
    }
}
