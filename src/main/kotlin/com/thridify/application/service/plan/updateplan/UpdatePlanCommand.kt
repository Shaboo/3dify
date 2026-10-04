package com.thridify.application.service.plan.updateplan

import java.util.UUID

data class UpdatePlanCommand(
    val id: UUID,
    val displayName: String?,
    val description: String?,
    val priceCents: Int?,
    val rateLimitRpm: Int?,
    val monthlyQuota: Int?,
    val sortOrder: Int?,
)
