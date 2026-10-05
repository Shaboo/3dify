package com.thridify.infrastructure.provider.runpod

import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.shared.metrics.AppMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

@Service
class RunPodClient(
    @Value("\${omni3d.runpod.api-url}") private val apiUrl: String,
    @Value("\${omni3d.runpod.api-key}") private val apiKey: String,
    @Value("\${omni3d.runpod.webhook-url}") private val webhookUrl: String,
    private val metrics: AppMetrics,
    @Value("\${omni3d.runpod.enabled:false}") private val enabled: Boolean = false,
    http: RestClient? = null,
) : GenerationProviderClient {
    private val restClient = http ?: RestClient.builder().requestFactory(
        org.springframework.http.client.JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
        ).apply { setReadTimeout(Duration.ofSeconds(20)) },
    ).build()

    override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String {
        try {
            check(enabled && apiKey.isNotBlank() && apiUrl.isNotBlank() && webhookUrl.isNotBlank()) { "RunPod generation is not configured" }
            val endpoint = URI(apiUrl)
            check(endpoint.scheme == "https" && endpoint.host in setOf("api.runpod.ai", "api.runpod.io") && Regex("/v2/[a-zA-Z0-9_-]+/run").matches(endpoint.path)) { "Configure the RunPod asynchronous /run endpoint" }
            val callback = URI(webhookUrl)
            check(callback.scheme == "https" && callback.host != null && callback.query == null && callback.fragment == null) { "RunPod requires a public HTTPS callback URL" }
            val response = restClient.post().uri(endpoint).header("Authorization", "Bearer $apiKey")
                .body(
                    mapOf(
                        "input" to mapOf("jobId" to jobId.toString(), "image1" to inputImage1, "image2" to inputImage2, "outputPrefix" to "outputs/$jobId/"),
                        "webhook" to "${webhookUrl.trimEnd('/')}/$jobId",
                    ),
                ).retrieve().body(RunPodResponse::class.java)
            val externalId = response?.id?.takeIf { it.isNotBlank() } ?: error("RunPod returned no task ID")
            check(response.status in setOf("IN_QUEUE", "IN_PROGRESS")) { "RunPod did not accept the task" }
            metrics.runpodDispatched.increment()
            return externalId
        } catch (ex: Exception) {
            metrics.runpodDispatchErrors.increment()
            // Keep upstream response bodies and credentials out of application errors.
            throw IllegalStateException("GPU provider could not accept the generation task", ex)
        }
    }
}

data class RunPodResponse(val id: String?, val status: String?)
