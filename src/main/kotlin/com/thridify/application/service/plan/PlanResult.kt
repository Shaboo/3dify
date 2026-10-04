package com.thridify.application.service.plan

import java.util.UUID

data class PlanResult(
    val id: UUID,
    val name: String,
    val displayName: String?,
    val description: String?,
    val priceCents: Int,
    val currency: String,
    val rateLimitRpm: Int,
    val monthlyQuota: Int,
    val sortOrder: Int,
    val stripePriceId: String?,
    val isActive: Boolean,
)
