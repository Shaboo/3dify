package com.thridify.infrastructure.provider.meshy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

@Component
class MeshyClient(private val config: MeshyProperties, private val inputs: MeshyInputImages, http: RestClient? = null) : GenerationProviderClient {
    private val log = LoggerFactory.getLogger(javaClass)
    init {
        require(config.requestTimeoutSeconds > 0) { "Meshy request timeout must be positive" }
    }
    override val name = "meshy"
    override val maxInputImages = 4
    private val mapper = jacksonObjectMapper()
    private val client = http ?: RestClient.builder().requestFactory(
        org.springframework.http.client.JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()).apply { setReadTimeout(Duration.ofSeconds(config.requestTimeoutSeconds)) },
    ).build()
    private var nextRequestNanos = 0L
    private var providerBlockedUntilNanos = 0L

    override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String = startGeneration(jobId, listOfNotNull(inputImage1, inputImage2?.takeIf { it.isNotBlank() }))
    override fun startGeneration(jobId: UUID, inputImages: List<String>): String {
        validateInputImages(inputImages.size)
        configured()
        // Conversion stays in the provider adapter: callers can continue uploading WebP.
        val images = try {
            inputImages.map(inputs::dataUri)
        } catch (_: Exception) {
            throw GenerationProviderException(false, "Generation input could not be prepared")
        }
        val kind = if (images.size == 1) "image-to-3d" else "multi-image-to-3d"
        val payload = mutableMapOf<String, Any>(
            "ai_model" to config.aiModel,
            "should_texture" to true,
            "target_formats" to listOf("glb", "usdz"),
        )
        if (images.size == 1) payload["image_url"] = images.single() else payload["image_urls"] = images
        val response = request(true) { client.post().uri("$BASE/$kind").header("Authorization", "Bearer ${config.apiKey}").body(payload).retrieve().toEntity(String::class.java).let(::readResponse) }
        val id = response?.path("result")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() && it.length <= 200 }
            ?: throw GenerationProviderException(true, "Generation provider returned an invalid task acknowledgement")
        // The endpoint kind is part of the opaque adapter handle, not any public response.
        return "$kind:$id"
    }
    override fun retrieveTask(taskId: String): GenerationProviderResult {
        configured()
        val (kind, id) = handle(taskId)
        val response = request(false) { client.get().uri("$BASE/$kind/{id}", id).header("Authorization", "Bearer ${config.apiKey}").retrieve().toEntity(String::class.java).let(::readResponse) }
            ?: throw GenerationProviderException(false, "Generation provider returned no task state")
        check(response.path("id").asText() == id) { "Generation provider task identity mismatch" }
        return when (response.path("status").asText()) {
            "PENDING", "IN_PROGRESS" -> GenerationProviderResult.Pending

            "SUCCEEDED" -> {
                val glb = response.path("model_urls").path("glb").asText()
                val usdz = response.path("model_urls").path("usdz").asText()
                if (glb.isBlank() || usdz.isBlank()) {
                    GenerationProviderResult.Failed("Generation provider returned incomplete model outputs")
                } else {
                    GenerationProviderResult.Succeeded(glb, usdz)
                }
            }

            "FAILED", "CANCELED" -> GenerationProviderResult.Failed()

            else -> throw GenerationProviderException(false, "Generation provider returned an invalid task state")
        }
    }
    override fun deleteTask(taskId: String) {
        configured()
        val (kind, id) = handle(taskId)
        try {
            request(false) { client.delete().uri("$BASE/$kind/{id}", id).header("Authorization", "Bearer ${config.apiKey}").retrieve().toBodilessEntity() }
        } catch (ex: MeshyHttpException) {
            if (ex.status != 404) throw ex
        }
    }
    private fun handle(handle: String): Pair<String, String> {
        val parts = handle.split(':', limit = 2)
        require(parts.size == 2 && parts[0] in setOf("image-to-3d", "multi-image-to-3d") && parts[1].isNotBlank()) { "Invalid generation task handle" }
        return parts[0] to parts[1]
    }
    private fun configured() {
        if (!config.enabled || config.apiKey.isBlank()) throw GenerationProviderException(false, "Meshy generation is not configured")
        if (config.aiModel !in setOf("meshy-6-lite", "meshy-6", "meshy-7.1", "latest")) throw GenerationProviderException(false, "Unsupported Meshy model")
    }

    @Synchronized
    private fun <T> request(submission: Boolean, action: () -> T): T {
        val providerDelay = providerBlockedUntilNanos - System.nanoTime()
        if (providerDelay > 0) throw GenerationProviderException(false, "Generation provider requested a retry delay", true, (providerDelay / 1_000_000_000).coerceAtLeast(1))
        val delay = nextRequestNanos - System.nanoTime()
        if (delay > 0) Thread.sleep(delay / 1_000_000, (delay % 1_000_000).toInt())
        nextRequestNanos = System.nanoTime() + config.requestIntervalMs.coerceAtLeast(0) * 1_000_000
        return try {
            action()
        } catch (ex: RestClientResponseException) {
            val retryAfter = retrySeconds(ex.responseHeaders?.getFirst("Retry-After"))
            if (ex.statusCode.value() == 429) providerBlockedUntilNanos = System.nanoTime() + retryAfter * 1_000_000_000
            log.warn("Meshy request rejected operation={} http_status={}", if (submission) "submission" else "task", ex.statusCode.value())
            throw MeshyHttpException(ex.statusCode.value(), submission && ex.statusCode.value() >= 500, retryAfter)
        } catch (ex: GenerationProviderException) {
            throw ex
        } catch (ex: Exception) {
            val causes = generateSequence<Throwable>(ex) { it.cause }.take(10).toList()
            val timedOut = causes.any { it is java.net.http.HttpTimeoutException || it is java.net.SocketTimeoutException }
            // Do not log exception messages, request bodies, signed URLs or API credentials.
            log.warn("Meshy request failed operation={} timeout={} error_type={} cause_type={}", if (submission) "submission" else "task", timedOut, ex.javaClass.simpleName, causes.last().javaClass.simpleName)
            throw GenerationProviderException(submission, if (timedOut) "Generation provider request timed out" else "Generation provider is temporarily unavailable")
        }
    }
    private fun readResponse(response: ResponseEntity<String>): JsonNode? {
        response.headers.getFirst("Retry-After")?.let { providerBlockedUntilNanos = System.nanoTime() + retrySeconds(it) * 1_000_000_000 }
        return response.body?.let(mapper::readTree)
    }
    private fun retrySeconds(value: String?): Long = value?.toLongOrNull()?.coerceIn(1, 86400) ?: 30
    private companion object {
        const val BASE = "https://api.meshy.ai/openapi/v1"
    }
}

private class MeshyHttpException(val status: Int, ambiguous: Boolean, afterSeconds: Long) : GenerationProviderException(ambiguous, "Generation provider request failed (HTTP $status)", status == 429, afterSeconds)
