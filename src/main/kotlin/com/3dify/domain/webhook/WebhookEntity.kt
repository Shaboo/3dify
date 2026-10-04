package com.`3dify`.domain.webhook

import java.time.OffsetDateTime
import java.util.UUID

data class WebhookEntity(
    val id: UUID,
    val userId: UUID,
    val url: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
)
