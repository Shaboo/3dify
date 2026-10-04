package com.`3dify`.infrastructure.webhook

import com.`3dify`.domain.generation.CustomerWebhookClient
import com.`3dify`.domain.generation.JobNotification
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class HttpCustomerWebhookClient : CustomerWebhookClient {
    private val client = RestClient.create()
    override fun deliver(url: String, notification: JobNotification) {
        client.post().uri(url).body(
            mapOf(
                "jobId" to notification.jobId.toString(),
                "status" to notification.status,
                "outputGlbUrl" to notification.glbUrl,
                "outputUsdzUrl" to notification.usdzUrl,
            ),
        ).retrieve().toBodilessEntity()
    }
}
