package com.`3dify`.domain.subscription

import com.`3dify`.domain.plan.PlanEntity
import com.`3dify`.domain.subscription.SubscriptionWithPlanEntity
import com.`3dify`.shared.exception.BadRequestException
import com.`3dify`.shared.exception.NotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

sealed interface CheckoutDecision {
    data object ActivateFree : CheckoutDecision
    data class Paid(val priceId: String) : CheckoutDecision
}

@Component
class SubscriptionPolicy {
    fun checkout(plan: PlanEntity?, id: UUID): CheckoutDecision {
        val found = plan ?: throw NotFoundException("Plan not found: $id")
        if (found.priceCents == 0) return CheckoutDecision.ActivateFree
        val priceId = found.stripePriceId
        if (priceId.isNullOrBlank()) throw BadRequestException("Plan is not linked to a Stripe price")
        return CheckoutDecision.Paid(priceId)
    }
    fun portalCustomer(subscription: SubscriptionWithPlanEntity?): String {
        val found = subscription ?: throw BadRequestException("No active subscription found")
        return found.stripeCustomerId ?: throw BadRequestException("No Stripe customer linked to this subscription")
    }
    fun isActive(status: String?) = status in listOf("active", "trialing")
    fun shouldReactivateKeys(status: String) = status == "active"
}
