package com.thridify.domain.outbox

import java.time.OffsetDateTime
import java.util.UUID

data class OutboxMessageEntity(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
)
