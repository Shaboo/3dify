package com.omni3d.application.service

import com.omni3d.shared.exception.NotFoundException
import com.omni3d.interfaces.rest.dto.WebhookResponse
import com.omni3d.infrastructure.persistence.WebhookRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WebhookService(private val webhookRepository: WebhookRepository) {

    fun getWebhook(userId: UUID): WebhookResponse? =
        webhookRepository.findByUserId(userId)?.let { w ->
            WebhookResponse(
                id        = w.id,
                url       = w.url,
                createdAt = w.createdAt.toString(),
                updatedAt = w.updatedAt.toString()
            )
        }

    fun setWebhook(userId: UUID, url: String): WebhookResponse {
        webhookRepository.upsert(userId, url)
        return getWebhook(userId)!!
    }

    fun deleteWebhook(userId: UUID) {
        val deleted = webhookRepository.deleteByUserId(userId)
        if (deleted == 0) throw NotFoundException("No webhook configured")
    }

    fun resolveWebhookUrl(userId: UUID): String? =
        webhookRepository.findByUserId(userId)?.url
}
