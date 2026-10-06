package com.thridify.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BacklogSamplingFailureTest {
    @Test
    fun `database failure is visible and cannot masquerade as an empty backlog`() {
        val dsl = mockk<DSLContext>()
        every { dsl.fetchOne(any<String>()) } throws IllegalStateException("private database details")
        val registry = SimpleMeterRegistry()
        val metrics = BacklogMetrics(dsl, registry)
        metrics.refresh()
        assertEquals(1.0, registry.get("omni3d.backlog.sample.failures").counter().count())
        assertTrue(registry.get("omni3d.backlog.items").tag("queue", "outbox").gauge().value().isNaN())
        assertTrue(registry.get("omni3d.backlog.last.success").gauge().value().isNaN())
    }
}
