package com.`3dify`.domain.plan

import com.`3dify`.domain.plan.PlanEntity
import com.`3dify`.shared.exception.NotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PlanPolicy {
    fun requirePlan(plan: PlanEntity?, id: UUID): PlanEntity = plan ?: throw NotFoundException("Plan $id not found")
    fun requiresBillingPrice(priceCents: Int) = priceCents > 0
}
