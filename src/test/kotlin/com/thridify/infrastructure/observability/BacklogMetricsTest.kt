package com.thridify.infrastructure.observability

import com.thridify.IntegrationTestBase
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BacklogMetricsTest : IntegrationTestBase() {
    @Test
    fun `persisted backlog remains visible without workers and resets when work finishes`() {
        resetDatabase()
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val metrics = BacklogMetrics(dsl, registry)
        val id = UUID.randomUUID()
        dsl.execute("INSERT INTO outbox_messages (id, aggregate_type, aggregate_id, payload, created_at) VALUES (?, 'JOB', ?, '{}'::jsonb, now() - interval '2 minutes')", id, UUID.randomUUID())
        metrics.refresh()
        val scrape = registry.scrape()
        assertTrue(scrape.contains("omni3d_backlog_oldest_age_seconds"))
        assertTrue(scrape.contains("omni3d_backlog_last_success_seconds"))
        assertTrue(scrape.contains("omni3d_backlog_items"))
        assertEquals(1.0, registry.get("omni3d.backlog.items").tag("queue", "outbox").gauge().value())
        assertTrue(registry.get("omni3d.backlog.oldest.age").tag("queue", "outbox").gauge().value() >= 120.0)
        assertEquals(0.0, registry.get("omni3d.backlog.items").tag("queue", "generation_uncertain").gauge().value())
        assertTrue(registry.get("omni3d.backlog.last.success").gauge().value() > 0)
        dsl.execute("UPDATE outbox_messages SET published_at = now() WHERE id = ?", id)
        metrics.refresh()
        assertEquals(0.0, registry.get("omni3d.backlog.items").tag("queue", "outbox").gauge().value())
        assertEquals(0.0, registry.get("omni3d.backlog.oldest.age").tag("queue", "outbox").gauge().value())
    }
}
