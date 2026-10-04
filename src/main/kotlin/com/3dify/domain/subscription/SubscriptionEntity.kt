package com.`3dify`.domain.subscription

import java.time.OffsetDateTime
import java.util.UUID

data class SubscriptionEntity(
    val id: UUID,
    val userId: UUID,
    val planId: UUID,
    val stripeSubscriptionId: String?,
    val stripeCustomerId: String?,
    val status: String,
    val currentPeriodEnd: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
)
