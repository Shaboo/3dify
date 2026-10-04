package com.`3dify`.domain.webhook

import com.`3dify`.domain.webhook.WebhookEntity
import java.util.UUID

interface WebhookRepository {
    fun findByUserId(userId: UUID): WebhookEntity?
    fun upsert(userId: UUID, url: String): Unit
    fun deleteByUserId(userId: UUID): Int
}
