package com.thridify.application.service.webhook.set

import com.thridify.application.service.webhook.toResult
import com.thridify.domain.webhook.WebhookRepository
import org.springframework.stereotype.Service

@Service
class SetWebhookApplicationService(private val webhooks: WebhookRepository, private val policy: com.thridify.domain.webhook.WebhookPolicy) {
    fun execute(command: SetWebhookCommand) = with(command) {
        policy.destination(url)
        webhooks.upsert(userId, url)
        webhooks.findByUserId(userId)!!.toResult()
    }
}
