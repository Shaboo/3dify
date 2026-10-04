package com.thridify.application.service.plan.listactive

import com.thridify.application.service.plan.toResult
import com.thridify.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class ListActivePlansApplicationService(private val plans: PlanRepository) {
    fun execute() = plans.findAllActive().map { it.toResult() }
}
