package com.`3dify`.domain.outbox

import com.`3dify`.domain.outbox.OutboxMessageEntity
import java.util.UUID

interface OutboxRepository {
    fun insert(aggregateType: String, aggregateId: UUID, payload: String): Unit
    fun findUnpublished(limit: Int): List<OutboxMessageEntity>
    fun markPublished(id: UUID): Unit
}
