package com.`3dify`.application.service.plan.listactive

import com.`3dify`.application.service.plan.toResult
import com.`3dify`.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class ListActivePlansApplicationService(private val plans: PlanRepository) {
    fun execute() = plans.findAllActive().map { it.toResult() }
}
