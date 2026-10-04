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

    private fun service(taskPublisher: GenerationTaskPublisher = publisher) = GenerateModelApplicationService(storage, jobs, history, taskPublisher, transactions, GenerationPolicy(), metrics)

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
    fun `empty images fail before storage or job persistence`() {
        val command = command().copy(image2 = GenerationImage(byteArrayOf(), "empty.png", "image/png"))
        assertThrows<BadRequestException> { service().execute(command) }
        verify(exactly = 0) { storage.upload(any(), any(), any()) }
    }

    @Test
    fun `transaction provider preserves the previous checked exception commit rule`() {
        val id = UUID.randomUUID()
        assertThrows<java.io.IOException> {
            transactions.transaction {
                dsl.execute("INSERT INTO users (id,email,password_hash) VALUES (?, 'checked@example.com', 'hash')", id)
                throw java.io.IOException("checked failure")
            }
        }
        assertEquals(1, dsl.fetchOne("SELECT count(*) AS total FROM users WHERE id=?", id)!!.get("total", Int::class.java))
    }
}
