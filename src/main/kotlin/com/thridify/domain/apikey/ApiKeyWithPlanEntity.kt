package com.thridify.domain.apikey

import java.time.OffsetDateTime
import java.util.UUID

data class ApiKeyWithPlanEntity(
    val id: UUID,
    val keyPrefix: String,
    val label: String?,
    val planName: String,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)
