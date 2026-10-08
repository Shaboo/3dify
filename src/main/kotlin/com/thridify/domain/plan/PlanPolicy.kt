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
    fun ensureValidCreation(name: String, displayName: String, rateLimit: Int, quota: Int, price: Int, currency: String) {
        if (name.isBlank() || name.length > 50 || displayName.isBlank() || displayName.length > 100 || rateLimit <= 0 || quota < 0 || price < 0 || !currency.matches(Regex("[a-z]{3}"))) throw com.thridify.shared.exception.BadRequestException("Invalid plan name, limits, price, or currency")
    }
    fun ensureNameAvailable(existing: PlanEntity?) {
        if (existing != null) throw com.thridify.shared.exception.ConflictException("A plan with this name already exists")
    }
    fun requiresBillingPrice(priceCents: Int) = priceCents > 0
}
