package com.thridify.application.service.generation

import com.thridify.IntegrationTestBase
import com.thridify.application.service.generation.submit.GenerateModelApplicationService
import com.thridify.application.service.generation.submit.GenerateModelCommand
import com.thridify.application.service.generation.submit.GenerationImage
import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.GenerationTaskPublisher
import com.thridify.domain.generation.ImageStorage
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.exception.BadRequestException
import com.thridify.shared.metrics.AppMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

class GenerateModelTransactionTest : IntegrationTestBase() {
    @Autowired private lateinit var pending: com.thridify.domain.generation.PendingInputRepository

    @Autowired private lateinit var providers: com.thridify.domain.generation.GenerationProviderRegistry

    @Autowired private lateinit var jobs: JobRepository

    @Autowired private lateinit var history: JobHistoryRepository

    @Autowired private lateinit var publisher: GenerationTaskPublisher

    @Autowired private lateinit var transactions: TransactionProvider

    @Autowired private lateinit var metrics: AppMetrics
    private val storage: ImageStorage = mockk()

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
        every { storage.delete(any()) } returns Unit
        every { storage.upload(any(), any(), any()) } returns "stored"
    }

    private fun apiKey(): UUID {
        val user = UUID.randomUUID()
        val key = UUID.randomUUID()
        dsl.execute("INSERT INTO users (id,email,password_hash) VALUES (?, 'rollback@example.com', 'hash')", user)
        createDirectWorkspace(user)
        dsl.execute("INSERT INTO api_keys (id,workspace_id,billing_scope_id,created_by_user_id,plan_id,key_hash,key_prefix) SELECT ?,?,?,?,id,'hash','prefix' FROM plans WHERE name='free'", key, workspaceId(user), scopeId(user), user)
        return key
    }

    private fun command() = GenerateModelCommand(
        apiKey(),
        GenerationImage(byteArrayOf(1), "first.png", "image/png"),
        GenerationImage(byteArrayOf(2), "second.png", "image/png"),
    )

    private fun service(taskPublisher: GenerationTaskPublisher = publisher, registry: com.thridify.domain.generation.GenerationProviderRegistry = providers) = GenerateModelApplicationService(storage, jobs, history, taskPublisher, transactions, GenerationPolicy(), metrics, pending, registry)

    @Test
    fun `backend persists and queues one hundred views for a provider without a ceiling`() {
        val provider = object : com.thridify.domain.generation.GenerationProviderClient {
            override val name = "future"
            override val maxInputImages: Int? = null
            override fun startGeneration(jobId: UUID, inputImages: List<String>) = error("Unused external submission")
            override fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?) = error("Unused")
            override fun retrieveTask(taskId: String) = error("Unused")
            override fun deleteTask(taskId: String) = Unit
        }
        val registry = object : com.thridify.domain.generation.GenerationProviderRegistry {
            override fun current() = provider
            override fun named(name: String) = provider
        }
        val photos = List(100) { GenerationImage(byteArrayOf(1), "$it.png", "image/png") }
        val result = service(registry = registry).execute(GenerateModelCommand(apiKey(), photos))
        val job = jobs.findById(result.jobId)!!
        assertEquals(100, job.inputImages.size)
        assertEquals(100, job.inputImages.distinct().size)
        val payload = dsl.fetchOne("SELECT payload::text AS payload FROM outbox_messages WHERE aggregate_id = ?", result.jobId)!!.get("payload", String::class.java)
        val queued = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(payload).path("imageKeys").map { it.asText() }
        assertEquals(job.inputImages, queued)
        verify(exactly = 100) { storage.upload(any(), any(), "image/png") }
    }

    @Test
    fun `provider count rejection happens before uploads or job creation`() {
        val input = command()
        assertThrows<BadRequestException> { service().execute(input.copy(images = List(5) { input.images.first() })) }
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM jobs")!!.get("total", Int::class.java))
    }

    @Test
    fun `outbox failure rolls back the job history and outbox after uploads`() {
        val failingPublisher = object : GenerationTaskPublisher {
            override fun publish(jobId: UUID, imageKey1: String, imageKey2: String) {
                publisher.publish(jobId, imageKey1, imageKey2)
                throw IllegalStateException("outbox failure")
            }
        }
        assertThrows<IllegalStateException> { service(failingPublisher).execute(command()) }
        for (table in listOf("jobs", "job_history", "outbox_messages")) {
            assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM $table")!!.get("total", Int::class.java))
        }
        verify(exactly = 2) { storage.upload(any(), any(), "image/png") }
    }

    @Test
    fun `upload failure does not persist a job or queue a task`() {
        every { storage.upload(any(), any(), any()) } throws IllegalStateException("storage failure")
        assertThrows<IllegalStateException> { service().execute(command()) }
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM jobs")!!.get("total", Int::class.java))
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM outbox_messages")!!.get("total", Int::class.java))
    }

    @Test
    fun `failed cleanup remains durable and a later cleanup run removes it`() {
        every { storage.upload(any(), any(), any()) } throws IllegalStateException("upload failure")
        every { storage.delete(any()) } throws IllegalStateException("delete failure")
        assertThrows<IllegalStateException> { service().execute(command()) }
        assertEquals(2, dsl.fetchCount(org.jooq.impl.DSL.table("pending_input_uploads")))
        dsl.execute("UPDATE pending_input_uploads SET created_at = now() - interval '2 days'")
        every { storage.delete(any()) } returns Unit
        com.thridify.application.service.generation.cleanup.CleanupGenerationInputsApplicationService(pending, storage, GenerationPolicy()).execute()
        assertEquals(0, dsl.fetchCount(org.jooq.impl.DSL.table("pending_input_uploads")))
    }

    @Test
    fun `empty images fail before storage or job persistence`() {
        val command = command().let { it.copy(images = listOf(it.images[0], GenerationImage(byteArrayOf(), "empty.png", "image/png"))) }
        assertThrows<BadRequestException> { service().execute(command) }
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
    }

    @Test
    fun `transaction provider rolls back checked failures too`() {
        val id = UUID.randomUUID()
        assertThrows<java.io.IOException> {
            transactions.transaction {
                dsl.execute("INSERT INTO users (id,email,password_hash) VALUES (?, 'checked@example.com', 'hash')", id)
                throw java.io.IOException("checked failure")
            }
        }
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM users WHERE id=?", id)!!.get("total", Int::class.java))
    }
}
