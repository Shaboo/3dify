package com.thridify.application.service.subscription.status

import java.util.UUID

data class SubscriptionStatusResult(
    val planId: UUID?,
    val planName: String?,
    val displayName: String?,
    val priceCents: Int?,
    val status: String?,
    val currentPeriodEnd: String?,
    val isActive: Boolean,
)
