package com.thridify.infrastructure.outbox

import java.util.UUID

interface GenerationTaskDelivery {
    fun deliver(message: OutboxMessageEntity): UUID
}
