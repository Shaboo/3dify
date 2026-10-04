package com.thridify.domain.subscription

import java.time.OffsetDateTime
import java.util.UUID

data class SubscriptionWithPlanEntity(
    val id: UUID,
    val userId: UUID,
    val planId: UUID,
    val stripeSubscriptionId: String?,
    val stripeCustomerId: String?,
    val status: String,
    val currentPeriodEnd: OffsetDateTime?,
    // Plan fields
    val planName: String,
    val planDisplayName: String,
    val planRateLimitRpm: Int,
    val planMonthlyQuota: Int,
    val planPriceCents: Int,
)
