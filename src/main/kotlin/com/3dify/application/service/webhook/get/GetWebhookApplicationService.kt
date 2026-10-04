package com.`3dify`.application.service.webhook.get

import com.`3dify`.application.service.webhook.toResult
import com.`3dify`.domain.webhook.WebhookRepository
import com.`3dify`.shared.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class GetWebhookApplicationService(private val webhooks: WebhookRepository) {
    fun execute(query: GetWebhookQuery) = (webhooks.findByUserId(query.userId) ?: throw NotFoundException("No webhook configured")).toResult()
}
