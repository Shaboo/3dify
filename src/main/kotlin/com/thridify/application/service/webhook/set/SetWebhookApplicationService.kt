package com.thridify.application.service.webhook.set

import com.thridify.application.service.webhook.toResult
import com.thridify.domain.webhook.WebhookRepository
import org.springframework.stereotype.Service

@Service
class SetWebhookApplicationService(private val webhooks: WebhookRepository) {
    fun execute(command: SetWebhookCommand) = with(command) {
        webhooks.upsert(userId, url)
        webhooks.findByUserId(userId)!!.toResult()
    }
}
