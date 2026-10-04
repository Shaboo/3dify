package com.thridify.interfaces.webhook

import com.thridify.application.service.generation.callback.GenerationCallbackResult
import com.thridify.application.service.generation.callback.HandleGenerationCallbackApplicationService
import com.thridify.application.service.generation.callback.HandleGenerationCallbackCommand
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/webhooks")
class ProviderWebhookController(private val handleCallback: HandleGenerationCallbackApplicationService) {
    private val log = LoggerFactory.getLogger(javaClass)
    data class RunPodWebhookPayload(
        val id: String,
        val status: String,
        val output: RunPodOutput?,
    )

    data class RunPodOutput(
        val glb: String?,
        val usdz: String?,
    )

    @PostMapping("/runpod/{jobId}")
    fun handleRunPodWebhook(@PathVariable jobId: UUID, @RequestBody payload: RunPodWebhookPayload): ResponseEntity<Void> = try {
        when (
            handleCallback.execute(
                HandleGenerationCallbackCommand(
                    jobId,
                    payload.id,
                    payload.status,
                    payload.output != null,
                    payload.output?.glb,
                    payload.output?.usdz,
                ),
            )
        ) {
            GenerationCallbackResult.ACCEPTED -> ResponseEntity.ok().build()
            GenerationCallbackResult.TASK_MISMATCH -> ResponseEntity.badRequest().build()
        }
    } catch (ex: Exception) {
        log.error("Error processing RunPod callback for job {}: {}", jobId, ex.message, ex)
        ResponseEntity.internalServerError().build()
    }
}
