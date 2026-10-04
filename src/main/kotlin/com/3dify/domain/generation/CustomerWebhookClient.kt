package com.`3dify`.domain.generation

interface CustomerWebhookClient {
    fun deliver(url: String, notification: JobNotification)
}
