package com.thridify.domain.generation
interface CustomerWebhookPublisher {
    fun publish(url: String, notification: JobNotification)
}
