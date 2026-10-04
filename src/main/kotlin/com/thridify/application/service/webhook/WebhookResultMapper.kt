package com.thridify.application.service.webhook

import com.thridify.application.service.webhook.WebhookResult
import com.thridify.domain.webhook.WebhookEntity

internal fun WebhookEntity.toResult() = WebhookResult(id, url, createdAt.toString(), updatedAt.toString())
