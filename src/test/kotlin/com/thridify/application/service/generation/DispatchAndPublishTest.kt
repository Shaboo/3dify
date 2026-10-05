package com.thridify.application.service.generation

import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class DispatchAndPublishTest {
    private val jobs: JobRepository = mockk(relaxed = true)
    private val history: JobHistoryRepository = mockk(relaxed = true)
    private val provider: GenerationProviderClient = mockk()
    private val providers: GenerationProviderRegistry = mockk()
    private val tasks: GenerationProviderTaskRepository = mockk(relaxed = true)
    private val transactions: TransactionProvider = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    init {
        every { providers.current() } returns provider
        every { provider.name } returns "meshy"
        every { tasks.reserve(any(), any()) } returns true
        every { transactions.transaction(any<() -> Any>()) } answers { firstArg<() -> Any>().invoke() }
    }
    private fun service() = DispatchGenerationTaskApplicationService(jobs, history, providers, tasks, transactions, metrics)

    @Test
    fun `dispatch reserves then records qualified provider task after external submission`() {
        val id = UUID.randomUUID()
        every { provider.startGeneration(id, "one", "two") } returns "external-id"
        service().execute(DispatchGenerationTaskCommand(id, "one", "two"))
        verifyOrder {
            tasks.reserve(id, "meshy")
            jobs.updateStatus(id, "PROCESSING")
            provider.startGeneration(id, "one", "two")
            jobs.updateExternalTaskId(id, "meshy:external-id")
            tasks.acknowledge(id, "external-id")
        }
        assertEquals(1.0, metrics.jobsDispatched.count())
    }

    @Test
    fun `uncertain provider submission is not silently retryable`() {
        val id = UUID.randomUUID()
        every { provider.startGeneration(any(), any(), any()) } throws GenerationProviderException(true, "timeout")
        service().execute(DispatchGenerationTaskCommand(id, "one", "two"))
        verify { tasks.submissionFailed(id, true) }
        verify { jobs.markFailed(id, "Generation provider could not accept the task") }
        verify(exactly = 0) { jobs.updateExternalTaskId(any(), any()) }
        assertEquals(1.0, metrics.jobsFailed.count())
    }

    @Test
    fun `redelivery with an existing reservation makes no upstream mutation`() {
        every { tasks.reserve(any(), any()) } returns false
        service().execute(DispatchGenerationTaskCommand(UUID.randomUUID(), "one", "two"))
        verify(exactly = 0) { provider.startGeneration(any(), any(), any()) }
    }
}
