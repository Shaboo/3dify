package com.`3dify`.application.service.webhook

import com.`3dify`.application.service.webhook.WebhookResult
import com.`3dify`.domain.webhook.WebhookEntity

internal fun WebhookEntity.toResult() = WebhookResult(id, url, createdAt.toString(), updatedAt.toString())
