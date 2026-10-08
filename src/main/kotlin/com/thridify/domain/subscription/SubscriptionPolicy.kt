package com.thridify.domain.subscription

import com.thridify.domain.plan.PlanEntity
import com.thridify.domain.subscription.SubscriptionWithPlanEntity
import com.thridify.shared.exception.BadRequestException
import com.thridify.shared.exception.NotFoundException
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
        if (!found.isActive) throw NotFoundException("Plan not found: $id")
        if (found.priceCents == 0) return CheckoutDecision.ActivateFree
        val priceId = found.stripePriceId
        if (priceId.isNullOrBlank()) throw BadRequestException("Plan is not linked to a Stripe price")
        return CheckoutDecision.Paid(priceId)
    }
    fun ensureFreeActivation(subscription: SubscriptionWithPlanEntity?) {
        if (subscription?.stripeSubscriptionId != null || subscription?.stripeCustomerId != null) throw BadRequestException("Cancel the paid subscription in the billing portal before choosing a free plan")
    }
    fun portalCustomer(subscription: SubscriptionWithPlanEntity?): String {
        val found = subscription ?: throw BadRequestException("No active subscription found")
        return found.stripeCustomerId ?: throw BadRequestException("No Stripe customer linked to this subscription")
    }
    fun currentEvent(created: java.time.OffsetDateTime, latest: java.time.OffsetDateTime?) = latest == null || !created.isBefore(latest)
    fun activateCheckout(current: SubscriptionWithPlanEntity?, subscriptionId: String, status: String) = isActive(status) && (current?.stripeSubscriptionId == null || current.stripeSubscriptionId == subscriptionId)
    fun isActive(status: String?) = status in listOf("active", "trialing")
}
