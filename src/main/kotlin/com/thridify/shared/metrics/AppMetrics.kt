package com.thridify.shared.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Central registry of all Omni3D custom metrics.
 *
 * Naming convention: omni3d.<domain>.<action>
 * Labels (tags) are used for results and categories — never for high-cardinality values like UUIDs.
 */
@Component
class AppMetrics(private val registry: MeterRegistry) {

    // ─── Users ──────────────────────────────────────────────────────────────
    val usersRegistered: Counter = Counter.builder("omni3d.users.registered")
        .description("Total number of successful user registrations")
        .register(registry)

    val usersLoginSuccess: Counter = Counter.builder("omni3d.users.logins")
        .tag("result", "success")
        .description("Successful logins")
        .register(registry)

    val usersLoginFailed: Counter = Counter.builder("omni3d.users.logins")
        .tag("result", "failed")
        .description("Failed logins (bad password or user not found)")
        .register(registry)

    // ─── API Keys ────────────────────────────────────────────────────────────
    val apiKeysCreated: Counter = Counter.builder("omni3d.api_keys.created")
        .description("Total API keys created")
        .register(registry)

    val apiKeysRevoked: Counter = Counter.builder("omni3d.api_keys.revoked")
        .description("Total API keys revoked")
        .register(registry)

    val apiKeyValidations: Counter = Counter.builder("omni3d.api_keys.validations")
        .tag("result", "valid")
        .description("Successful API key validations")
        .register(registry)

    val apiKeyValidationsFailed: Counter = Counter.builder("omni3d.api_keys.validations")
        .tag("result", "invalid")
        .description("Failed API key validations")
        .register(registry)

    // ─── Rate Limiting ────────────────────────────────────────────────────────
    val rateLimitRejections: Counter = Counter.builder("omni3d.rate_limit.rejected")
        .description("Requests rejected by rate limiter")
        .register(registry)

    // ─── Jobs ─────────────────────────────────────────────────────────────────
    val jobsCreated: Counter = Counter.builder("omni3d.jobs.created")
        .description("Total 3D generation jobs submitted")
        .register(registry)

    val jobsDispatched: Counter = Counter.builder("omni3d.jobs.dispatched")
        .description("Jobs successfully dispatched to GPU provider")
        .register(registry)

    val jobsCompleted: Counter = Counter.builder("omni3d.jobs.completed")
        .tag("result", "success")
        .description("Jobs completed successfully")
        .register(registry)

    val jobsFailed: Counter = Counter.builder("omni3d.jobs.completed")
        .tag("result", "failed")
        .description("Jobs that failed")
        .register(registry)

    /** Record end-to-end job duration. Call with the total millis from creation to completion. */
    private val jobDurationTimer: Timer = Timer.builder("omni3d.jobs.duration")
        .description("End-to-end duration of 3D generation jobs (submitted to result)")
        .publishPercentileHistogram()
        .register(registry)

    fun recordJobDuration(durationMs: Long) = jobDurationTimer.record(durationMs, TimeUnit.MILLISECONDS)

    // ─── Outbox ───────────────────────────────────────────────────────────────
    val outboxPublished: Counter = Counter.builder("omni3d.outbox.published")
        .description("Outbox messages successfully published to RabbitMQ")
        .register(registry)

    val outboxFailed: Counter = Counter.builder("omni3d.outbox.failed")
        .description("Outbox messages that failed to publish")
        .register(registry)

    // ─── RunPod / GPU ─────────────────────────────────────────────────────────
    val runpodDispatched: Counter = Counter.builder("omni3d.runpod.dispatched")
        .description("Jobs sent to RunPod GPU")
        .register(registry)

    val runpodCallbacks: Counter = Counter.builder("omni3d.runpod.callbacks")
        .tag("result", "success")
        .description("RunPod completion callbacks received (success)")
        .register(registry)

    val runpodCallbacksFailed: Counter = Counter.builder("omni3d.runpod.callbacks")
        .tag("result", "failed")
        .description("RunPod completion callbacks received (failure outcome)")
        .register(registry)

    val runpodDispatchErrors: Counter = Counter.builder("omni3d.runpod.errors")
        .description("RunPod dispatch or communication errors")
        .register(registry)

    // ─── Subscriptions ────────────────────────────────────────────────────────
    val subscriptionsActivated: Counter = Counter.builder("omni3d.subscriptions.activated")
        .description("Subscriptions activated (new or reinstated)")
        .register(registry)

    val subscriptionsCanceled: Counter = Counter.builder("omni3d.subscriptions.canceled")
        .description("Subscriptions canceled")
        .register(registry)

    val subscriptionsPastDue: Counter = Counter.builder("omni3d.subscriptions.past_due")
        .description("Subscriptions gone past_due due to failed payment")
        .register(registry)

    // ─── User Webhooks ────────────────────────────────────────────────────────
    val webhookDeliveriesSuccess: Counter = Counter.builder("omni3d.webhooks.deliveries")
        .tag("result", "success")
        .description("Webhook deliveries that succeeded (2xx response)")
        .register(registry)

    val webhookDeliveriesFailed: Counter = Counter.builder("omni3d.webhooks.deliveries")
        .tag("result", "failed")
        .description("Webhook deliveries that failed (non-2xx or connection error)")
        .register(registry)

    // ─── Auth Failures ────────────────────────────────────────────────────────
    val authFailures: Counter = Counter.builder("omni3d.auth.failures")
        .tag("reason", "invalid_key")
        .description("API key auth failures -- invalid or revoked key")
        .register(registry)

    val authFailuresSubscription: Counter = Counter.builder("omni3d.auth.failures")
        .tag("reason", "no_subscription")
        .description("API key auth failures -- no active subscription")
        .register(registry)

    val authFailuresRateLimit: Counter = Counter.builder("omni3d.auth.failures")
        .tag("reason", "rate_limit")
        .description("API key auth failures -- rate limit exceeded")
        .register(registry)

    // ─── Storage ──────────────────────────────────────────────────────────────
    val storageUploads: Counter = Counter.builder("omni3d.storage.uploads")
        .tag("result", "success")
        .description("Successful file uploads to R2")
        .register(registry)

    val storageUploadErrors: Counter = Counter.builder("omni3d.storage.uploads")
        .tag("result", "error")
        .description("Failed file uploads to R2")
        .register(registry)
}
