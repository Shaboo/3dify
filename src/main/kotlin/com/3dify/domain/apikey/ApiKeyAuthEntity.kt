package com.`3dify`.domain.apikey

import java.util.UUID

data class ApiKeyAuthEntity(
    val id: UUID,
    val userId: UUID,
    val isActive: Boolean,
    val rateLimitRpm: Int,
)
