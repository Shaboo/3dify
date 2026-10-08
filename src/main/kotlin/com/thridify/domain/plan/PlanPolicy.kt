package com.thridify.domain.plan

import com.thridify.domain.plan.PlanEntity
import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PlanPolicy {
    fun requirePlan(plan: PlanEntity?, id: UUID): PlanEntity = plan ?: throw NotFoundException("Plan $id not found")
    fun ensurePriceUnchanged(plan: PlanEntity, priceCents: Int?) {
        if (priceCents != null && priceCents != plan.priceCents) throw com.thridify.shared.exception.BadRequestException("Create a new plan to change its price; existing billing offers have fixed prices")
    }
    fun requiresBillingPrice(priceCents: Int) = priceCents > 0
}
