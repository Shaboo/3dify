package com.thridify.application.service.generation

import com.thridify.application.service.generation.callback.GenerationCallbackResult
import com.thridify.application.service.generation.callback.HandleGenerationCallbackApplicationService
import com.thridify.application.service.generation.callback.HandleGenerationCallbackCommand
import com.thridify.domain.generation.CustomerWebhookClient
import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.JobNotification
import com.thridify.domain.job.JobEntity
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.webhook.WebhookEntity
import com.thridify.domain.webhook.WebhookRepository
import com.thridify.shared.metrics.AppMetrics
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
        val result = service.execute(HandleGenerationCallbackCommand(id, "task", "COMPLETED", true, "https://assets/model.glb", "https://assets/model.usdz"))
        assertEquals(GenerationCallbackResult.ACCEPTED, result)
        verifyOrder {
            jobs.markSuccess(id, "https://assets/model.glb", "https://assets/model.usdz")
            history.insert(id, "SUCCESS", "GLB: https://assets/model.glb | USDZ: https://assets/model.usdz")
            client.deliver("https://customer/callback", JobNotification(id, "SUCCESS", "https://assets/model.glb", "https://assets/model.usdz"))
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
    fun `completed callback without an output object fails the job`() {
        every { jobs.findByExternalTaskId("task") } returns job
        every { jobs.findUserIdByJobId(id) } returns null
        assertEquals(
            GenerationCallbackResult.ACCEPTED,
            service.execute(HandleGenerationCallbackCommand(id, "task", "COMPLETED", false, null, null)),
        )
        verify { history.insert(id, "FAILED", "GPU Provider returned incomplete model outputs") }
        verify { client wasNot Called }
        verify(exactly = 0) { jobs.markSuccess(any(), any(), any()) }
        verify(exactly = 1) { jobs.markFailed(id, "GPU Provider returned incomplete model outputs") }
    }
}
