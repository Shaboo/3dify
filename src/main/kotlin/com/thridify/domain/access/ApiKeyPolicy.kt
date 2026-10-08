package com.thridify.domain.access

import com.thridify.domain.apikey.ApiKeyAuthEntity
import com.thridify.domain.plan.PlanEntity
import com.thridify.domain.subscription.SubscriptionWithPlanEntity
import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom

@Component
class ApiKeyPolicy {
    private val random = SecureRandom()
    fun createRawKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return "omni_pk_" + (1..40).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
    fun hash(rawKey: String) = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }
    fun hasValidPrefix(rawKey: String) = rawKey.startsWith("omni_pk_")
    fun isValid(key: ApiKeyAuthEntity?) = key != null && key.isActive
    fun requirePlan(plan: PlanEntity?, name: String): PlanEntity = plan ?: throw NotFoundException("Plan '$name' not found")
    fun ensurePlanAllowed(plan: PlanEntity, subscription: SubscriptionWithPlanEntity?) {
        if (!plan.isActive) throw NotFoundException("Plan is not available")
        val permitted = if (subscription?.status in setOf("active", "trialing")) plan.id == subscription?.planId else plan.name == "free"
        if (!permitted) throw com.thridify.shared.exception.ApiException(403, "Choose the plan on your subscription")
    }
    fun ensureRevoked(count: Int) {
        if (count == 0) throw NotFoundException("API key not found")
    }
    fun subscriptionDenial(subscription: SubscriptionWithPlanEntity?): String? = when (subscription?.status) {
        null -> "No active subscription"
        "active", "trialing" -> null
        "past_due" -> "Subscription payment overdue -- please update your billing details"
        "canceled" -> "Subscription canceled -- please re-subscribe at the dashboard"
        else -> "Subscription inactive"
    }
}
