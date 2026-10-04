package com.thridify.application.service.webhook.get

import com.thridify.application.service.webhook.toResult
import com.thridify.domain.webhook.WebhookRepository
import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class GetWebhookApplicationService(private val webhooks: WebhookRepository) {
    fun execute(query: GetWebhookQuery) = (webhooks.findByUserId(query.userId) ?: throw NotFoundException("No webhook configured")).toResult()
}
