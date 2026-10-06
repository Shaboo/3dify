package com.thridify.infrastructure.provider

import com.thridify.IntegrationTestBase
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.thridify.application.service.generation.reconcile.ReconcileGenerationTasksApplicationService
import com.thridify.domain.generation.GenerationOutputStorage
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.generation.GenerationProviderResult
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.generation.RetainedGenerationOutputs
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Import(GenerationLifecycleTest.Ports::class)
class GenerationLifecycleTest : IntegrationTestBase() {
    @Autowired private lateinit var dispatch: DispatchGenerationTaskApplicationService

    @Autowired private lateinit var reconcile: ReconcileGenerationTasksApplicationService

    @Autowired private lateinit var providers: GenerationProviderRegistry

    @Autowired private lateinit var outputs: GenerationOutputStorage

    @Autowired private lateinit var jobs: JobRepository

    @Autowired private lateinit var tasks: GenerationProviderTaskRepository

    @Autowired private lateinit var transactions: TransactionProvider
    private val meshy: GenerationProviderClient = mockk()
    private val runpod: GenerationProviderClient = mockk()

    @TestConfiguration
    class Ports {
        @Bean @Primary
        fun providers(): GenerationProviderRegistry = mockk()

        @Bean @Primary
        fun outputs(): GenerationOutputStorage = mockk()
    }

    @BeforeEach
    fun setup() {
        resetDatabase()
        clearMocks(providers, outputs)
        every { meshy.name } returns "meshy"
        every { meshy.validateInputImages(any()) } just Runs
        every { runpod.validateInputImages(any()) } just Runs
        every { runpod.name } returns "runpod"
        every { providers.current() } returns meshy
        every { providers.named("meshy") } returns meshy
        every { providers.named("runpod") } returns runpod
        every { meshy.startGeneration(any(), any(), any()) } returns "multi-image-to-3d:task"
        every { runpod.startGeneration(any(), any(), any()) } returns "runpod-task"
        every { meshy.retrieveTask(any()) } returns GenerationProviderResult.Succeeded("https://assets.meshy.ai/source.glb", "https://assets.meshy.ai/source.usdz")
        every { outputs.retain(any(), any(), any(), any()) } answers {
            val id = firstArg<UUID>()
            RetainedGenerationOutputs("https://models.example/outputs/$id/model.glb", "https://models.example/outputs/$id/model.usdz")
        }
        every { outputs.delete(any()) } just Runs
    }
    private fun job(): UUID {
        val workspace = UUID.randomUUID()
        val scope = UUID.randomUUID()
        val id = UUID.randomUUID()
        dsl.execute("INSERT INTO workspaces(id, name) VALUES (?, 'Test')", workspace)
        dsl.execute("INSERT INTO billing_scopes(id, workspace_id) VALUES (?, ?)", scope, workspace)
        dsl.execute("INSERT INTO jobs(id, workspace_id, billing_scope_id, input_image_1, input_image_2) VALUES (?, ?, ?, 'one', 'two')", id, workspace, scope)
        return id
    }

    @Test
    fun `dispatch pin survives provider switch and completion publishes durable outputs once`() {
        val id = job()
        val command = DispatchGenerationTaskCommand(id, "one", "two")
        dispatch.execute(command)
        every { providers.current() } returns runpod
        dispatch.execute(command)
        reconcile.execute()
        reconcile.execute()
        val completed = jobs.findById(id)!!
        assertEquals("SUCCESS", completed.status)
        assertEquals("https://models.example/outputs/$id/model.glb", completed.outputGlbUrl)
        assertEquals("complete", dsl.fetchOne("SELECT state FROM generation_provider_tasks WHERE job_id = ?", id)!!.get("state", String::class.java))
        verify(exactly = 1) { meshy.startGeneration(id, "one", "two") }
        verify(exactly = 0) { runpod.startGeneration(any(), any(), any()) }
        verify(exactly = 1) { outputs.retain(id, "meshy", any(), any()) }
        assertEquals(1, dsl.fetchOne("SELECT count(*) AS total FROM job_history WHERE job_id = ? AND status = 'SUCCESS'", id)!!.get("total", Int::class.java))
        val next = job()
        dispatch.execute(DispatchGenerationTaskCommand(next, "one", "two"))
        verify(exactly = 1) { runpod.startGeneration(next, "one", "two") }
    }

    @Test
    fun `concurrent Rabbit redelivery issues only one charged provider submission`() {
        val id = job()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { pool.submit { dispatch.execute(DispatchGenerationTaskCommand(id, "one", "two")) } }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        verify(exactly = 1) { meshy.startGeneration(id, "one", "two") }
        assertEquals("PROCESSING", jobs.findById(id)!!.status)
    }

    @Test
    fun `output retention failure retries retrieval without resubmission or premature success`() {
        val id = job()
        dispatch.execute(DispatchGenerationTaskCommand(id, "one", "two"))
        every { outputs.retain(any(), any(), any(), any()) } throws IllegalStateException("storage offline")
        reconcile.execute()
        assertEquals("PROCESSING", jobs.findById(id)!!.status)
        assertEquals(null, jobs.findById(id)!!.outputGlbUrl)
        every { outputs.retain(any(), any(), any(), any()) } returns RetainedGenerationOutputs("https://models.example/model.glb", "https://models.example/model.usdz")
        dsl.execute("UPDATE generation_provider_tasks SET next_poll_at = now() WHERE job_id = ?", id)
        reconcile.execute()
        assertEquals("SUCCESS", jobs.findById(id)!!.status)
        verify(exactly = 1) { meshy.startGeneration(any(), any(), any()) }
    }

    @Test
    fun `provider task errors map to FAILED without output download`() {
        val id = job()
        dispatch.execute(DispatchGenerationTaskCommand(id, "one", "two"))
        every { meshy.retrieveTask(any()) } returns GenerationProviderResult.Failed()
        reconcile.execute()
        assertEquals("FAILED", jobs.findById(id)!!.status)
        verify(exactly = 0) { outputs.retain(any(), any(), any(), any()) }
    }

    @Test
    fun `outputs finishing after job deletion are cleaned rather than resurrecting redacted data`() {
        val id = job()
        dispatch.execute(DispatchGenerationTaskCommand(id, "one", "two"))
        every { outputs.retain(any(), any(), any(), any()) } answers {
            dsl.execute("DELETE FROM jobs WHERE id = ?", id)
            RetainedGenerationOutputs("https://models.example/model.glb", "https://models.example/model.usdz")
        }
        reconcile.execute()
        assertEquals(null, jobs.findById(id))
        verify(exactly = 1) { outputs.delete(id) }
    }

    @Test
    fun `four-photo retry uses every persisted view after the queue delivery is gone`() {
        val id = job()
        val keys = listOf("front", "back", "left", "right")
        dsl.execute("UPDATE jobs SET input_images = ? WHERE id = ?", keys.toTypedArray(), id)
        var calls = 0
        every { meshy.startGeneration(id, keys) } answers {
            if (calls++ == 0) throw GenerationProviderException(false, "queue full", true)
            "multi-image-to-3d:retried-four"
        }
        dispatch.execute(DispatchGenerationTaskCommand(id, keys))
        dsl.execute("UPDATE generation_provider_tasks SET next_poll_at = now() WHERE job_id = ?", id)
        reconcile.execute()
        assertEquals("meshy:multi-image-to-3d:retried-four", jobs.findById(id)!!.externalTaskId)
        verify(exactly = 2) { meshy.startGeneration(id, keys) }
        verify(exactly = 0) { meshy.startGeneration(any(), any(), any()) }
    }

    @Test
    fun `explicit capacity rejection retries the same job while ambiguous submission never retries`() {
        var calls = 0
        every { meshy.startGeneration(any(), any(), any()) } answers {
            if (calls++ == 0) throw GenerationProviderException(false, "queue full", true)
            "multi-image-to-3d:retried-task"
        }
        val id = job()
        dispatch.execute(DispatchGenerationTaskCommand(id, "one", "two"))
        assertEquals("PROCESSING", jobs.findById(id)!!.status)
        dsl.execute("UPDATE generation_provider_tasks SET next_poll_at = now() WHERE job_id = ?", id)
        reconcile.execute()
        assertEquals("meshy:multi-image-to-3d:retried-task", jobs.findById(id)!!.externalTaskId)
        reconcile.execute()
        assertEquals("SUCCESS", jobs.findById(id)!!.status)
        verify(exactly = 2) { meshy.startGeneration(id, "one", "two") }
        val uncertain = job()
        every { meshy.startGeneration(any(), any(), any()) } throws GenerationProviderException(true, "connection lost")
        dispatch.execute(DispatchGenerationTaskCommand(uncertain, "one", "two"))
        dispatch.execute(DispatchGenerationTaskCommand(uncertain, "one", "two"))
        reconcile.execute()
        assertEquals("uncertain", dsl.fetchOne("SELECT state FROM generation_provider_tasks WHERE job_id = ?", uncertain)!!.get("state", String::class.java))
        verify(exactly = 1) { meshy.startGeneration(uncertain, "one", "two") }
    }

    @Test
    fun `another reconciliation worker cannot claim a leased task`() {
        val id = job()
        dispatch.execute(DispatchGenerationTaskCommand(id, "one", "two"))
        assertEquals(1, transactions.transaction { tasks.claimDue(10) }.size)
        assertEquals(0, transactions.transaction { tasks.claimDue(10) }.size)
    }
}
