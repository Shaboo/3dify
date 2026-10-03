package com.omni3d.api.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.api.service.JobService
import com.omni3d.api.service.RunPodClient
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.*

@Component
class TaskWorker(
    private val objectMapper: ObjectMapper,
    private val jobService: JobService,
    private val runPodClient: RunPodClient
) {

    private val log = LoggerFactory.getLogger(TaskWorker::class.java)
    private val restClient = RestClient.create()

    @RabbitListener(queues = ["\${omni3d.rabbitmq.queue}"])
    fun handleTask(messageJson: String) {
        val message = objectMapper.readValue(messageJson, TaskProducer.TaskMessage::class.java)
        val jobId = message.jobId

        log.info("Worker picked up job: {}", jobId)

        try {
            // Mark as processing
            jobService.markProcessing(jobId)

            // Asynchronously dispatch the job to the GPU provider.
            // This is non-blocking. It returns immediately with the provider's task ID.
            val externalTaskId = runPodClient.startGeneration(
                jobId = jobId,
                inputImage1 = message.inputImage1Key,
                inputImage2 = message.inputImage2Key
            )

            // Save the external task ID to the database so we can look it up when the webhook fires.
            jobService.updateExternalTaskId(jobId, externalTaskId)

            log.info("Job {} dispatched successfully to provider as task {}", jobId, externalTaskId)

            // NOTE: We do NOT mark the job as success or fire the user webhook here.
            // That will happen exclusively in the ProviderWebhookController when RunPod calls us back.

        } catch (ex: Exception) {
            log.error("Failed to dispatch job {} to provider: {}", jobId, ex.message, ex)
            jobService.markFailed(jobId, "GPU Provider Error: \${ex.message}")

            // We do not fire the user webhook here either; the ProviderWebhookController
            // isn't aware of this failure because RunPod never got it. If we wanted to,
            // we could inject webhookService here, but standardizing error handling
            // via a separate failed-job crawler is cleaner. For brevity of the mock,
            // we will let the user poll or they'll see FAILED in history.
        }
    }

}
