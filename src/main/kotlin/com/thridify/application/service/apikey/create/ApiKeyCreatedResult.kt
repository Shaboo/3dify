package com.thridify.application.service.apikey.create

import java.util.UUID

data class ApiKeyCreatedResult(
    val id: UUID,
    val key: String, // raw key — shown ONCE
    val label: String?,
    val planName: String,
    val createdAt: String,
)
