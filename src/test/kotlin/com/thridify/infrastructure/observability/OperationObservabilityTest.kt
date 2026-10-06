package com.thridify.infrastructure.observability

import com.thridify.shared.exception.BadRequestException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationObservabilityTest {
    @Test
    fun `spring proxies record successes rejections failures and active calls without changing results`() {
        AnnotationConfigApplicationContext().use { context ->
            val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            context.registerBean(MeterRegistry::class.java, java.util.function.Supplier { registry })
            context.register(OperationObservability::class.java, ObservationTestClient::class.java)
            context.refresh()
            val client = context.getBean(ObservationTestClient::class.java)
            assertEquals("ok", client.execute("success"))
            assertFailsWith<BadRequestException> { client.execute("rejected") }
            val failure = assertFailsWith<IllegalStateException> { client.execute("error") }
            assertSame(client.failure, failure)
            for (outcome in listOf("success", "rejected", "error")) {
                assertEquals(1, registry.get("omni3d.operations.duration").tags("operation", "ObservationTestClient.execute", "outcome", outcome).timer().count())
            }
            assertTrue(registry.scrape().contains("omni3d_operations_duration_seconds_count"))
            assertEquals(0, registry.get("omni3d.operations.active").tag("operation", "ObservationTestClient.execute").longTaskTimer().activeTasks())
        }
    }
}

open class ObservationTestClient {
    open val failure = IllegalStateException("secret must not be logged")
    open fun execute(outcome: String): String = when (outcome) {
        "rejected" -> throw BadRequestException("sensitive request detail")
        "error" -> throw failure
        else -> "ok"
    }
}
