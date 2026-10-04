package com.`3dify`.domain.generation

import com.`3dify`.domain.outbox.OutboxMessageEntity
import java.util.UUID

interface GenerationTaskDelivery {
    fun deliver(message: OutboxMessageEntity): UUID
}
