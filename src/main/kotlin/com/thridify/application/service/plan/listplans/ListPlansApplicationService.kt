package com.thridify.application.service.plan.listplans

import com.thridify.application.service.plan.toResult
import com.thridify.domain.plan.PlanRepository
import org.springframework.stereotype.Service

@Service
class ListPlansApplicationService(private val plans: PlanRepository) {
    fun execute() = plans.findAll().map { it.toResult() }
}
