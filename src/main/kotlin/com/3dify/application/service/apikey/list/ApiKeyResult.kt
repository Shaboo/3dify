package com.`3dify`.application.service.apikey.list

import java.util.UUID

data class ApiKeyResult(
    val id: UUID,
    val keyPrefix: String,
    val label: String?,
    val planName: String,
    val isActive: Boolean,
    val createdAt: String,
    val revokedAt: String?,
)
