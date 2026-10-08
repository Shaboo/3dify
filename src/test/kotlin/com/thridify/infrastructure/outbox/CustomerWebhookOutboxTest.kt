package com.thridify.infrastructure.outbox
import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.domain.generation.CustomerWebhookClient
import com.thridify.domain.generation.CustomerWebhookPublisher
import com.thridify.domain.generation.JobNotification
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
class CustomerWebhookOutboxTest : IntegrationTestBase() {
    @Autowired lateinit var publisher: CustomerWebhookPublisher

    @Autowired lateinit var transactions: TransactionProvider

    @Autowired lateinit var outbox: OutboxRepository

    @Autowired lateinit var mapper: ObjectMapper

    @Autowired lateinit var metrics: AppMetrics

    @Test fun `notification persists through customer outage and is retried with stable job ID`() {
        resetDatabase()
        val notification = JobNotification(UUID.randomUUID(), "SUCCESS", "glb", "usdz")
        val client: CustomerWebhookClient = mockk()
        val router = CustomerAndGenerationDelivery(mockk(), client, mapper, metrics)
        val relay = OutboxRelay(outbox, router, metrics)
        transactions.transaction { publisher.publish("https://customer.example/callback", notification) }
        every { client.deliver(any(), any()) } throws IllegalStateException("offline")
        relay.execute()
        assertEquals(1, dsl.fetchCount(org.jooq.impl.DSL.table("outbox_messages")))
        assertEquals(0, outbox.findUnpublished(50).size)
        assertEquals(null, dsl.fetchOne("SELECT published_at FROM outbox_messages")?.get("published_at"))
        dsl.execute("UPDATE outbox_messages SET next_attempt_at = now()")
        every { client.deliver(any(), any()) } returns Unit
        relay.execute()
        verify(exactly = 2) { client.deliver("https://customer.example/callback", notification) }
        assertEquals(0, dsl.fetchCount(org.jooq.impl.DSL.table("outbox_messages"), org.jooq.impl.DSL.field("published_at").isNull))
    }

    @Test fun `notification rolls back with completion transaction`() {
        resetDatabase()
        assertThrows<IllegalStateException> {
            transactions.transaction {
                publisher.publish("https://customer.example/callback", JobNotification(UUID.randomUUID(), "FAILED", null, null))
                error("completion failed")
            }
        }
        assertEquals(0, dsl.fetchCount(org.jooq.impl.DSL.table("outbox_messages")))
    }
}
