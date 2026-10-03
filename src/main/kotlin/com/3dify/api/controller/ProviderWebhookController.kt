package com.omni3d.api.controller

import com.omni3d.api.metrics.AppMetrics
import com.omni3d.api.service.JobService
import com.omni3d.api.service.WebhookService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import java.util.*

@RestController
@RequestMapping("/internal/webhooks")
class ProviderWebhookController(
    private val jobService: JobService,
    private val webhookService: WebhookService,
    private val metrics: AppMetrics
) {
    private val log = LoggerFactory.getLogger(ProviderWebhookController::class.java)
    private val restClient = RestClient.create()

    data class RunPodWebhookPayload(
        val id: String,
        val status: String,
        val output: RunPodOutput?
    )

    data class RunPodOutput(
        val glb: String?,
        val usdz: String?
    )

    @PostMapping("/runpod/{jobId}")
    fun handleRunPodWebhook(
        @PathVariable jobId: UUID,
        @RequestBody payload: RunPodWebhookPayload
    ): ResponseEntity<Void> {
        MDC.put("jobId", jobId.toString())
        try {
            log.info("RunPod callback received [jobId={}, taskId={}, status={}]", jobId, payload.id, payload.status)

            val verifiedJobId = jobService.findJobByExternalTaskId(payload.id)
            if (verifiedJobId == null || verifiedJobId != jobId) {
                log.warn("Webhook taskId {} does not map to job {} -- ignoring", payload.id, jobId)
                return ResponseEntity.badRequest().build()
            }

            when {
                payload.status == "COMPLETED" && payload.output != null -> {
                    val glb  = payload.output.glb  ?: ""
                    val usdz = payload.output.usdz ?: ""
                    jobService.markSuccess(jobId, glb, usdz)
                    metrics.runpodCallbacks.increment()
                    log.info("Job {} completed via RunPod callback", jobId)
                    fireUserWebhook(jobId, "SUCCESS", glb, usdz)
                }
                payload.status == "FAILED" -> {
                    jobService.markFailed(jobId, "GPU Provider reported failure")
                    metrics.runpodCallbacksFailed.increment()
                    log.warn("Job {} failed via RunPod callback", jobId)
                    fireUserWebhook(jobId, "FAILED", null, null)
                }
                else -> {
                    log.info("Job {} in-progress state received [status={}]", jobId, payload.status)
                }
            }

            return ResponseEntity.ok().build()

        } catch (ex: Exception) {
            log.error("Error processing RunPod callback for job {}: {}", jobId, ex.message, ex)
            return ResponseEntity.internalServerError().build()
        } finally {
            MDC.remove("jobId")
        }
    }

    private fun fireUserWebhook(jobId: UUID, status: String, glbUrl: String?, usdzUrl: String?) {
        try {
            val userId     = jobService.getUserIdForJob(jobId) ?: return
            val webhookUrl = webhookService.resolveWebhookUrl(userId) ?: return

            val payload = mapOf(
                "jobId"         to jobId.toString(),
                "status"        to status,
                "outputGlbUrl"  to glbUrl,
                "outputUsdzUrl" to usdzUrl
            )

            restClient.post()
                .uri(webhookUrl)
                .body(payload)
                .retrieve()
                .toBodilessEntity()

            metrics.webhookDeliveriesSuccess.increment()
            log.info("User webhook delivered [jobId={}, url={}]", jobId, webhookUrl)

        } catch (ex: Exception) {
            metrics.webhookDeliveriesFailed.increment()
            log.warn("User webhook delivery failed [jobId={}]: {}", jobId, ex.message)
        }
    }
}
