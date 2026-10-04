package com.thridify.infrastructure.provider.runpod

import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.UUID

@Service
class RunPodClient(
    @Value("\${omni3d.runpod.api-url}") private val apiUrl: String,
    @Value("\${omni3d.runpod.api-key}") private val apiKey: String,
    @Value("\${omni3d.runpod.webhook-url}") private val webhookUrl: String,
    private val metrics: AppMetrics,
) : GenerationProviderClient {
    private val log = LoggerFactory.getLogger(RunPodClient::class.java)
    private val restClient = RestClient.create()

    /**
     * Starts an asynchronous 3D generation task on the GPU provider.
     * Returns the external task ID immediately — completion arrives via webhook callback.
     */
    override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String {
        log.info("Dispatching job {} to RunPod [image1={}, image2={}]", jobId, inputImage1, inputImage2)

        val payload = mapOf(
            "input" to mapOf(
                "image1" to inputImage1,
                "image2" to inputImage2,
            ),
            "webhook" to "$webhookUrl/$jobId",
        )

        return try {
            // ── Real call (uncomment when RunPod is provisioned): ──────────
            // val response = restClient.post()
            //     .uri(apiUrl)
            //     .header("Authorization", "Bearer $apiKey")
            //     .body(payload)
            //     .retrieve()
            //     .body(RunPodResponse::class.java)
            // val externalId = response?.id ?: throw IllegalStateException("No task ID from RunPod")
            // ──────────────────────────────────────────────────────────────

            val mockTaskId = "runpod-mock-${UUID.randomUUID()}"
            log.info("Mock RunPod accepted job {} => externalTaskId={}", jobId, mockTaskId)

            simulateProviderDelayAndCallback(jobId, mockTaskId, "http://localhost:8080/internal/webhooks/runpod")
            metrics.runpodDispatched.increment()
            mockTaskId
        } catch (ex: Exception) {
            metrics.runpodDispatchErrors.increment()
            log.error("RunPod dispatch failed for job {}: {}", jobId, ex.message, ex)
            throw RuntimeException("GPU provider error: ${ex.message}", ex)
        }
    }

    private fun simulateProviderDelayAndCallback(jobId: UUID, taskId: String, targetUrl: String) {
        Thread {
            try {
                log.info("Mock GPU spinning up for task {} (job {})...", taskId, jobId)
                Thread.sleep(5000)

                val mockGlbUrl = "https://r2.omni3d.com/outputs/$jobId/model.glb"
                val mockUsdzUrl = "https://r2.omni3d.com/outputs/$jobId/model.usdz"

                val cbPayload = mapOf(
                    "id" to taskId,
                    "status" to "COMPLETED",
                    "output" to mapOf("glb" to mockGlbUrl, "usdz" to mockUsdzUrl),
                )

                log.info("Mock GPU done — calling webhook [taskId={}, url={}/{}]", taskId, targetUrl, jobId)
                restClient.post()
                    .uri("$targetUrl/$jobId")
                    .body(cbPayload)
                    .retrieve()
                    .toBodilessEntity()
            } catch (ex: Exception) {
                log.error("Mock RunPod callback failed for task {}: {}", taskId, ex.message, ex)
            }
        }.start()
    }
}
