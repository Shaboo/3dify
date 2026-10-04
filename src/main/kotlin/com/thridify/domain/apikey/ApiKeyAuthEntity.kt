package com.thridify.domain.apikey

import java.util.UUID

data class ApiKeyAuthEntity(
    val id: UUID,
    val userId: UUID,
    val isActive: Boolean,
    val rateLimitRpm: Int,
)
