package com.`3dify`.application.service.generation

import com.`3dify`.application.service.generation.callback.GenerationCallbackResult
import com.`3dify`.application.service.generation.callback.HandleGenerationCallbackApplicationService
import com.`3dify`.application.service.generation.callback.HandleGenerationCallbackCommand
import com.`3dify`.domain.generation.CustomerWebhookClient
import com.`3dify`.domain.generation.GenerationPolicy
import com.`3dify`.domain.generation.JobNotification
import com.`3dify`.domain.job.JobEntity
import com.`3dify`.domain.job.JobHistoryRepository
import com.`3dify`.domain.job.JobRepository
import com.`3dify`.domain.webhook.WebhookEntity
import com.`3dify`.domain.webhook.WebhookRepository
import com.`3dify`.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

class CallbackDeliveryTest {
    private val jobs: JobRepository = mockk(relaxed = true)
    private val history: JobHistoryRepository = mockk(relaxed = true)
    private val webhooks: WebhookRepository = mockk()
    private val client: CustomerWebhookClient = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val service = HandleGenerationCallbackApplicationService(jobs, history, webhooks, client, GenerationPolicy(), metrics)
    private val id = UUID.randomUUID()
    private val user = UUID.randomUUID()
    private val job = JobEntity(id, UUID.randomUUID(), "PROCESSING", "task", "one", "two", null, null, null, null, OffsetDateTime.now(), null)

    @Test
    fun `customer delivery failure is swallowed after recording successful output`() {
        every { jobs.findByExternalTaskId("task") } returns job
        every { jobs.findById(id) } returns job
        every { jobs.findUserIdByJobId(id) } returns user
        every { webhooks.findByUserId(user) } returns WebhookEntity(UUID.randomUUID(), user, "https://customer/callback", OffsetDateTime.now(), null)
        every { client.deliver(any(), any()) } throws IllegalStateException("customer offline")
        val result = service.execute(HandleGenerationCallbackCommand(id, "task", "COMPLETED", true, null, null))
        assertEquals(GenerationCallbackResult.ACCEPTED, result)
        verifyOrder {
            jobs.markSuccess(id, "", "")
            history.insert(id, "SUCCESS", "GLB:  | USDZ: ")
            client.deliver("https://customer/callback", JobNotification(id, "SUCCESS", "", ""))
        }
        assertEquals(1.0, metrics.jobsCompleted.count())
        assertEquals(1.0, metrics.webhookDeliveriesFailed.count())
    }

    @Test
    fun `mismatched tasks have no writes or customer notifications`() {
        every { jobs.findByExternalTaskId("different") } returns null
        assertEquals(
            GenerationCallbackResult.TASK_MISMATCH,
            service.execute(HandleGenerationCallbackCommand(id, "different", "COMPLETED", true, "glb", "usdz")),
        )
        verify { history wasNot Called }
        verify { webhooks wasNot Called }
        verify { client wasNot Called }
        verify(exactly = 0) { jobs.markSuccess(any(), any(), any()) }
    }

    @Test
    fun `completed callback without an output object preserves processing state`() {
        every { jobs.findByExternalTaskId("task") } returns job
        assertEquals(
            GenerationCallbackResult.ACCEPTED,
            service.execute(HandleGenerationCallbackCommand(id, "task", "COMPLETED", false, null, null)),
        )
        verify { history wasNot Called }
        verify { client wasNot Called }
        verify(exactly = 0) { jobs.markSuccess(any(), any(), any()) }
        verify(exactly = 0) { jobs.markFailed(any(), any()) }
    }
}
