package com.thridify.application.service.webhook.delete

import com.thridify.domain.webhook.WebhookPolicy
import com.thridify.domain.webhook.WebhookRepository
import org.springframework.stereotype.Service

@Service
class DeleteWebhookApplicationService(private val webhooks: WebhookRepository, private val policy: WebhookPolicy) {
    fun execute(command: DeleteWebhookCommand) {
        policy.ensureDeleted(webhooks.deleteByUserId(command.userId))
    }
}
