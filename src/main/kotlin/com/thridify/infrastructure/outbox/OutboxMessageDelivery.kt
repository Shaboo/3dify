package com.thridify.infrastructure.outbox

import java.util.UUID

interface OutboxMessageDelivery {
    fun deliver(message: OutboxMessageEntity): UUID
}
