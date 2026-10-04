package com.omni3d.domain

import java.time.OffsetDateTime
import java.util.UUID

// ---------------------------------------------------------------------------
// User
// ---------------------------------------------------------------------------

data class UserEntity(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val name: String?,
    val isAdmin: Boolean
)

// ---------------------------------------------------------------------------
// Plan
// ---------------------------------------------------------------------------

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
    val sortOrder: Int
)

// ---------------------------------------------------------------------------
// API Key
// ---------------------------------------------------------------------------

data class ApiKeyEntity(
    val id: UUID,
    val userId: UUID,
    val planId: UUID,
    val keyHash: String,
    val keyPrefix: String,
    val label: String?,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?
)

/**
 * Projection returned when listing keys for a user — includes joined plan fields.
 */
data class ApiKeyWithPlanEntity(
    val id: UUID,
    val keyPrefix: String,
    val label: String?,
    val planName: String,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?
)

/**
 * Minimal projection used during authentication/rate-limiting.
 */
data class ApiKeyAuthEntity(
    val id: UUID,
    val userId: UUID,
    val isActive: Boolean,
    val rateLimitRpm: Int
)

// ---------------------------------------------------------------------------
// Job
// ---------------------------------------------------------------------------

data class JobEntity(
    val id: UUID,
    val apiKeyId: UUID,
    val status: String,
    val externalTaskId: String?,
    val inputImage1: String,
    val inputImage2: String,
    val outputGlbUrl: String?,
    val outputUsdzUrl: String?,
    val webhookUrl: String?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
)

// ---------------------------------------------------------------------------
// Job History
// ---------------------------------------------------------------------------

data class JobHistoryEntity(
    val id: UUID,
    val jobId: UUID,
    val status: String,
    val details: String?,
    val createdAt: OffsetDateTime
)

// ---------------------------------------------------------------------------
// Subscription
// ---------------------------------------------------------------------------

data class SubscriptionEntity(
    val id: UUID,
    val userId: UUID,
    val planId: UUID,
    val stripeSubscriptionId: String?,
    val stripeCustomerId: String?,
    val status: String,
    val currentPeriodEnd: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?
)

/**
 * Subscription joined with its plan — used for status queries.
 */
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
    val planPriceCents: Int
)

// ---------------------------------------------------------------------------
// Outbox Message
// ---------------------------------------------------------------------------

data class OutboxMessageEntity(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?
)

// ---------------------------------------------------------------------------
// Webhook
// ---------------------------------------------------------------------------

data class WebhookEntity(
    val id: UUID,
    val userId: UUID,
    val url: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?
)
