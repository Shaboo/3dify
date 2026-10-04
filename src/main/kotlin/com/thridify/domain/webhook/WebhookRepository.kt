package com.thridify.domain.webhook

import com.thridify.domain.webhook.WebhookEntity
import java.util.UUID

interface WebhookRepository {
    fun findByUserId(userId: UUID): WebhookEntity?
    fun upsert(userId: UUID, url: String): Unit
    fun deleteByUserId(userId: UUID): Int
}
