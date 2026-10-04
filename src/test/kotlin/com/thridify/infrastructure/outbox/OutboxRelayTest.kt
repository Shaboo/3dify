package com.thridify.infrastructure.outbox

import com.thridify.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

class OutboxRelayTest {
    private val metrics = AppMetrics(SimpleMeterRegistry())

    @Test
    fun `relay leaves failed delivery pending and continues to subsequent messages`() {
        val outbox: OutboxRepository = mockk(relaxed = true)
        val delivery: GenerationTaskDelivery = mockk()
        val first = OutboxMessageEntity(UUID.randomUUID(), "JOB", UUID.randomUUID(), "bad payload", OffsetDateTime.now(), null)
        val second = first.copy(id = UUID.randomUUID(), aggregateId = UUID.randomUUID(), payload = "good payload")
        every { outbox.findUnpublished(50) } returns listOf(first, second)
        every { delivery.deliver(first) } throws IllegalArgumentException("invalid payload")
        every { delivery.deliver(second) } returns second.aggregateId
        OutboxRelay(outbox, delivery, metrics).execute()
        verify(exactly = 0) { outbox.markPublished(first.id) }
        verify(exactly = 1) { outbox.markPublished(second.id) }
        assertEquals(1.0, metrics.outboxFailed.count())
        assertEquals(1.0, metrics.outboxPublished.count())
    }
}
