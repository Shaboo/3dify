package com.thridify.application.service.webhook.set

import java.util.UUID

data class SetWebhookCommand(val userId: UUID, val url: String)
