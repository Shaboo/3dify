package com.thridify.application.service.subscription.webhook

data class HandleStripeWebhookCommand(val payload: String, val signature: String)
