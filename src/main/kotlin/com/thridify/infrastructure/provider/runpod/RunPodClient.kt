package com.thridify.infrastructure.provider.runpod

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderResult
import com.thridify.shared.metrics.AppMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
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
    override val name = "runpod"
    private val restClient = http ?: RestClient.builder().requestFactory(
        org.springframework.http.client.JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
        ).apply { setReadTimeout(Duration.ofSeconds(20)) },
    ).build()

    override fun retrieveTask(taskId: String): GenerationProviderResult {
        check(enabled && apiKey.isNotBlank()) { "RunPod generation is not configured" }
        val response = try {
            restClient.get().uri("${apiUrl.removeSuffix("/run")}/status/{id}", taskId)
                .header("Authorization", "Bearer $apiKey").retrieve().body(String::class.java)?.let(jacksonObjectMapper()::readTree)
                ?: error("RunPod returned no task state")
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.value() == 404) return GenerationProviderResult.Failed("Generation provider task is no longer available")
            throw ex
        }
        check(response.path("id").asText() == taskId) { "RunPod task identity mismatch" }
        return when (response.path("status").asText()) {
            "IN_QUEUE", "IN_PROGRESS" -> GenerationProviderResult.Pending

            "COMPLETED" -> {
                val glb = response.path("output").path("glb").asText()
                val usdz = response.path("output").path("usdz").asText()
                if (glb.isBlank() || usdz.isBlank()) {
                    GenerationProviderResult.Failed("Generation provider returned incomplete model outputs")
                } else {
                    GenerationProviderResult.Succeeded(glb, usdz)
                }
            }

            "FAILED", "CANCELLED", "TIMED_OUT" -> GenerationProviderResult.Failed()

            else -> error("RunPod returned an invalid task state")
        }
    }
    override fun deleteTask(taskId: String) {
        // Worker outputs are deleted through managed storage; cancel any outstanding work
        // before privacy cleanup so a late worker cannot recreate deleted output objects.
        when (retrieveTask(taskId)) {
            GenerationProviderResult.Pending -> {
                restClient.post().uri("${apiUrl.removeSuffix("/run")}/cancel/{id}", taskId)
                    .header("Authorization", "Bearer $apiKey").retrieve().toBodilessEntity()
                error("RunPod cancellation must be confirmed before privacy cleanup")
            }

            else -> Unit
        }
    }

    override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String {
        try {
            if (!enabled || apiKey.isBlank() || apiUrl.isBlank() || webhookUrl.isBlank()) throw GenerationProviderException(false, "RunPod generation is not configured")
            val endpoint = URI(apiUrl)
            if (endpoint.scheme != "https" || endpoint.host !in setOf("api.runpod.ai", "api.runpod.io") || !Regex("/v2/[a-zA-Z0-9_-]+/run").matches(endpoint.path)) throw GenerationProviderException(false, "Configure the RunPod asynchronous /run endpoint")
            val callback = URI(webhookUrl)
            if (callback.scheme != "https" || callback.host == null || callback.query != null || callback.fragment != null) throw GenerationProviderException(false, "RunPod requires a public HTTPS callback URL")
            val response = restClient.post().uri(endpoint).header("Authorization", "Bearer $apiKey")
                .body(
                    mapOf(
                        "input" to mapOf("jobId" to jobId.toString(), "image1" to inputImage1, "image2" to inputImage2, "outputPrefix" to "outputs/$jobId/"),
                        "webhook" to "${webhookUrl.trimEnd('/')}/$jobId",
                    ),
                ).retrieve().body(RunPodResponse::class.java)
            val externalId = response?.id?.takeIf { it.isNotBlank() } ?: throw GenerationProviderException(true, "RunPod returned no task ID")
            if (response.status !in setOf("IN_QUEUE", "IN_PROGRESS", "COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT")) throw GenerationProviderException(true, "RunPod returned an invalid task state")
            metrics.runpodDispatched.increment()
            return externalId
        } catch (ex: Exception) {
            metrics.runpodDispatchErrors.increment()
            // Keep upstream response bodies and credentials out of application errors.
            when (ex) {
                is GenerationProviderException -> throw ex
                is RestClientResponseException -> throw GenerationProviderException(ex.statusCode.value() >= 500, "Generation provider request failed", ex.statusCode.value() == 429)
                else -> throw GenerationProviderException(true, "GPU provider could not accept the generation task")
            }
        }
    }
}

data class RunPodResponse(val id: String?, val status: String?)
