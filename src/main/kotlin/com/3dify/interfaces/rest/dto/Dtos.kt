package com.`3dify`.interfaces.rest.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

// --- Auth ---
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class AuthResponse(
    val token: String,
    val userId: UUID,
    val email: String,
    @JsonProperty("isAdmin") val isAdmin: Boolean = false,
)

// --- API Key ---
data class CreateApiKeyRequest(
    val label: String? = null,
    val planName: String = "free",
)

data class ApiKeyCreatedResponse(
    val id: UUID,
    val key: String, // raw key — shown ONCE
    val label: String?,
    val planName: String,
    val createdAt: String,
)

data class ApiKeyResponse(
    val id: UUID,
    val keyPrefix: String,
    val label: String?,
    val planName: String,
    @JsonProperty("isActive") val isActive: Boolean,
    val createdAt: String,
    val revokedAt: String?,
)

// --- Webhook ---
data class SetWebhookRequest(
    val url: String,
)

data class WebhookResponse(
    val id: UUID,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
)

// --- Generate / Jobs ---
data class GenerateResponse(
    val jobId: UUID,
    val status: String,
)

data class JobResponse(
    val id: UUID,
    val status: String,
    val inputImage1: String,
    val inputImage2: String,
    val outputGlbUrl: String?,
    val outputUsdzUrl: String?,
    val errorMessage: String?,
    val createdAt: String,
    val completedAt: String?,
)

// --- Job History ---
data class JobHistoryEntry(
    val id: UUID,
    val jobId: UUID,
    val status: String,
    val details: String?,
    val createdAt: String,
)

// --- Plans ---
data class PlanResponse(
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
    @JsonProperty("isActive") val isActive: Boolean,
)

data class CreatePlanRequest(
    val name: String,
    val displayName: String,
    val description: String? = null,
    val rateLimitRpm: Int,
    val monthlyQuota: Int,
    val priceCents: Int,
    val currency: String = "usd",
    val sortOrder: Int = 99,
)

data class UpdatePlanRequest(
    val displayName: String? = null,
    val description: String? = null,
    val priceCents: Int? = null,
    val rateLimitRpm: Int? = null,
    val monthlyQuota: Int? = null,
    val sortOrder: Int? = null,
)

// --- Subscriptions ---
data class SubscriptionStatusResponse(
    val planId: UUID?,
    val planName: String?,
    val displayName: String?,
    val priceCents: Int?,
    val status: String?,
    val currentPeriodEnd: String?,
    @JsonProperty("isActive") val isActive: Boolean,
)

data class CreateCheckoutRequest(
    val planId: UUID,
    val successUrl: String,
    val cancelUrl: String,
)

data class CheckoutResponse(
    val checkoutUrl: String,
)

data class PortalRequest(
    val returnUrl: String,
)

data class PortalResponse(
    val portalUrl: String,
)
