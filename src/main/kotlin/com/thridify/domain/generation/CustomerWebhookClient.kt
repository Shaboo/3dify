package com.thridify.domain.generation

interface CustomerWebhookClient {
    fun deliver(url: String, notification: JobNotification)
}
