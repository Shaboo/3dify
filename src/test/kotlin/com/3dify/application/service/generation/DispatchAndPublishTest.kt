package com.`3dify`.application.service.generation

import com.`3dify`.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.`3dify`.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.`3dify`.application.service.generation.publish.PublishPendingGenerationTasksApplicationService
import com.`3dify`.domain.generation.GenerationProviderClient
import com.`3dify`.domain.generation.GenerationTaskDelivery
import com.`3dify`.domain.job.JobHistoryRepository
import com.`3dify`.domain.job.JobRepository
import com.`3dify`.domain.outbox.OutboxMessageEntity
import com.`3dify`.domain.outbox.OutboxRepository
import com.`3dify`.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

class DispatchAndPublishTest {
    private val jobs: JobRepository = mockk(relaxed = true)
    private val history: JobHistoryRepository = mockk(relaxed = true)
    private val provider: GenerationProviderClient = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())

    @Test
    fun `dispatch stores provider task only after recording processing`() {
        val id = UUID.randomUUID()
        every { provider.startGeneration(id, "one", "two") } returns "external-id"
        DispatchGenerationTaskApplicationService(jobs, history, provider, metrics)
            .execute(DispatchGenerationTaskCommand(id, "one", "two"))
        verifyOrder {
            jobs.updateStatus(id, "PROCESSING")
            history.insert(id, "PROCESSING", "Worker picked up job")
            provider.startGeneration(id, "one", "two")
            jobs.updateExternalTaskId(id, "external-id")
            history.insert(id, "PROCESSING", "Job dispatched to GPU with ID: external-id")
        }
        assertEquals(1.0, metrics.jobsDispatched.count())
    }

    @Test
    fun `dispatch failure records failure with the existing literal error text`() {
        val id = UUID.randomUUID()
        every { provider.startGeneration(any(), any(), any()) } throws IllegalStateException("offline")
        every { jobs.findById(id) } returns null
        DispatchGenerationTaskApplicationService(jobs, history, provider, metrics)
            .execute(DispatchGenerationTaskCommand(id, "one", "two"))
        verify { jobs.markFailed(id, "GPU Provider Error: \${ex.message}") }
        verify { history.insert(id, "FAILED", "GPU Provider Error: \${ex.message}") }
        verify(exactly = 0) { jobs.updateExternalTaskId(any(), any()) }
        assertEquals(1.0, metrics.jobsFailed.count())
    }

    @Test
    fun `relay leaves failed delivery pending and continues to subsequent messages`() {
        val outbox: OutboxRepository = mockk(relaxed = true)
        val delivery: GenerationTaskDelivery = mockk()
        val first = OutboxMessageEntity(UUID.randomUUID(), "JOB", UUID.randomUUID(), "bad payload", OffsetDateTime.now(), null)
        val second = first.copy(id = UUID.randomUUID(), aggregateId = UUID.randomUUID(), payload = "good payload")
        every { outbox.findUnpublished(50) } returns listOf(first, second)
        every { delivery.deliver(first) } throws IllegalArgumentException("invalid payload")
        every { delivery.deliver(second) } returns second.aggregateId
        PublishPendingGenerationTasksApplicationService(outbox, delivery, metrics).execute()
        verify(exactly = 0) { outbox.markPublished(first.id) }
        verify(exactly = 1) { outbox.markPublished(second.id) }
        assertEquals(1.0, metrics.outboxFailed.count())
        assertEquals(1.0, metrics.outboxPublished.count())
    }
}
