package com.thridify.infrastructure.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/** Samples persisted work independently of workers, so disabled or stuck workers remain visible. */
@Component
class BacklogMetrics(private val dsl: DSLContext, private val registry: MeterRegistry) {
    private data class Backlog(val count: Double, val age: Double)

    @Volatile private var snapshot = emptyMap<String, Backlog>()

    @Volatile private var lastSuccess = Double.NaN
    private val log = LoggerFactory.getLogger(javaClass)
    private val states = listOf("submitting", "submitted", "retrying", "uncertain")

    init {
        for (queue in listOf("outbox", "shopify_privacy") + states.map { "generation_$it" }) {
            Gauge.builder("omni3d.backlog.items", this) { it.snapshot[queue]?.count ?: Double.NaN }
                .description("Persisted unfinished items, sampled every 30 seconds by default")
                .tag("queue", queue).register(registry)
            Gauge.builder("omni3d.backlog.oldest.age", this) { it.snapshot[queue]?.age ?: Double.NaN }
                .baseUnit("seconds").description("Age of oldest unfinished item; zero when empty")
                .tag("queue", queue).register(registry)
        }
        Gauge.builder("omni3d.backlog.last.success", this) { it.lastSuccess }
            .baseUnit("seconds").description("Unix timestamp of last successful backlog sample")
            .register(registry)
    }

    @Scheduled(fixedDelayString = "\${observability.backlog-delay-ms:30000}", initialDelayString = "\${observability.backlog-delay-ms:30000}")
    fun refresh() {
        try {
            val next = mutableMapOf<String, Backlog>()
            next["outbox"] = sample("SELECT count(*) AS count, coalesce(extract(epoch FROM now() - min(created_at)), 0) AS age FROM outbox_messages WHERE published_at IS NULL")
            next["shopify_privacy"] = sample("SELECT count(*) AS count, coalesce(extract(epoch FROM now() - min(received_at)), 0) AS age FROM shopify_webhook_receipts WHERE topic = 'shop/redact' AND completed_at IS NULL")
            states.forEach { next["generation_$it"] = Backlog(0.0, 0.0) }
            for (row in dsl.fetch("SELECT state, count(*) AS count, coalesce(extract(epoch FROM now() - min(created_at)), 0) AS age FROM generation_provider_tasks WHERE state IN ('submitting', 'submitted', 'retrying', 'uncertain') GROUP BY state")) {
                val state = row.get("state", String::class.java)
                next["generation_$state"] = Backlog(row.get("count", Double::class.java)!!, row.get("age", Double::class.java)!!.coerceAtLeast(0.0))
            }
            snapshot = next
            lastSuccess = Instant.now().epochSecond.toDouble()
        } catch (ex: Exception) {
            registry.counter("omni3d.backlog.sample.failures").increment()
            log.warn("backlog_sample_failed error_type={}", ex.javaClass.simpleName)
        }
    }

    private fun sample(sql: String): Backlog {
        val row = requireNotNull(dsl.fetchOne(sql))
        return Backlog(row.get("count", Double::class.java)!!, row.get("age", Double::class.java)!!.coerceAtLeast(0.0))
    }
}
