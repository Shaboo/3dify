package com.`3dify`.application.service.webhook

import java.util.UUID

data class WebhookResult(
    val id: UUID,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
)
