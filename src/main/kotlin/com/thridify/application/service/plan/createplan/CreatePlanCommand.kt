package com.thridify.application.service.plan.createplan

data class CreatePlanCommand(
    val name: String,
    val displayName: String,
    val description: String?,
    val rateLimitRpm: Int,
    val monthlyQuota: Int,
    val priceCents: Int,
    val currency: String,
    val sortOrder: Int,
)
