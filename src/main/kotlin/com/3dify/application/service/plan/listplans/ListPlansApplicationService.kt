package com.`3dify`.application.service.plan.listplans

import com.`3dify`.application.service.plan.toResult
import com.`3dify`.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class ListPlansApplicationService(private val plans: PlanRepository) {
    fun execute() = plans.findAll().map { it.toResult() }
}
