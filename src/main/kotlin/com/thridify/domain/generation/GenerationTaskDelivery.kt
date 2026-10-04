package com.thridify.domain.generation

import com.thridify.domain.outbox.OutboxMessageEntity
import java.util.UUID

interface GenerationTaskDelivery {
    fun deliver(message: OutboxMessageEntity): UUID
}
