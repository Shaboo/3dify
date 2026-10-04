package com.`3dify`.domain.apikey

import java.time.OffsetDateTime
import java.util.UUID

data class ApiKeyEntity(
    val id: UUID,
    val userId: UUID,
    val planId: UUID,
    val keyHash: String,
    val keyPrefix: String,
    val label: String?,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)
