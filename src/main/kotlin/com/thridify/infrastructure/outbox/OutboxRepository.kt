package com.thridify.infrastructure.outbox

import java.util.UUID

interface OutboxRepository {
    fun insert(aggregateType: String, aggregateId: UUID, payload: String): Unit
    fun findUnpublished(limit: Int): List<OutboxMessageEntity>
    fun retryLater(id: UUID): Unit
    fun markPublished(id: UUID): Unit
}
