package com.thridify.domain.plan

import com.thridify.domain.plan.PlanEntity
import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PlanPolicy {
    fun requirePlan(plan: PlanEntity?, id: UUID): PlanEntity = plan ?: throw NotFoundException("Plan $id not found")
    fun requiresBillingPrice(priceCents: Int) = priceCents > 0
}
