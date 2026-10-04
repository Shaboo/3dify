package com.`3dify`.application.service.subscription.webhook

data class HandleStripeWebhookCommand(val payload: String, val signature: String)
