package com.`3dify`.domain.plan

import java.util.UUID

data class PlanEntity(
    val id: UUID,
    val name: String,
    val displayName: String,
    val description: String?,
    val rateLimitRpm: Int,
    val monthlyQuota: Int,
    val priceCents: Int,
    val currency: String,
    val stripePriceId: String?,
    val isActive: Boolean,
    val sortOrder: Int,
)
