package com.`3dify`.application.service.webhook.delete

import com.`3dify`.domain.webhook.WebhookPolicy
import com.`3dify`.domain.webhook.WebhookRepository
import org.springframework.stereotype.Service

@Service
class DeleteWebhookApplicationService(private val webhooks: WebhookRepository, private val policy: WebhookPolicy) {
    fun execute(command: DeleteWebhookCommand) {
        policy.ensureDeleted(webhooks.deleteByUserId(command.userId))
    }
}
