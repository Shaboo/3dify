package com.thridify.application.service.access

import com.thridify.application.service.access.authorize.ApiAuthorizationResult
import com.thridify.application.service.access.authorize.AuthorizeApiRequestApplicationService
import com.thridify.application.service.access.authorize.AuthorizeApiRequestCommand
import com.thridify.domain.access.ApiKeyPolicy
import com.thridify.domain.access.RequestRateLimiter
import com.thridify.domain.apikey.ApiKeyAuthEntity
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.subscription.SubscriptionRepository
import com.thridify.domain.subscription.SubscriptionWithPlanEntity
import com.thridify.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthorizeApiRequestTest {
    private val keys: ApiKeyRepository = mockk()
    private val subscriptions: SubscriptionRepository = mockk()
    private val limiter: RequestRateLimiter = mockk()
    private val policy = ApiKeyPolicy()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val service = AuthorizeApiRequestApplicationService(keys, subscriptions, limiter, policy, metrics)
    private val key = ApiKeyAuthEntity(UUID.randomUUID(), UUID.randomUUID(), true, 60)
    private val command = AuthorizeApiRequestCommand("omni_pk_valid-key")

    private fun subscription(status: String): SubscriptionWithPlanEntity = mockk<SubscriptionWithPlanEntity>().also {
        every { it.status } returns status
        every { it.planRateLimitRpm } returns 60
    }

    @Test
    fun `subscription rejection never consumes rate limit capacity`() {
        every { keys.findByKeyHash(policy.hash(command.rawKey)) } returns key
        for ((status, message) in listOf(
            "past_due" to "Subscription payment overdue -- please update your billing details",
            "canceled" to "Subscription canceled -- please re-subscribe at the dashboard",
            "incomplete" to "Subscription inactive",
        )) {
            every { subscriptions.findActiveByUserId(key.userId) } returns subscription(status)
            val result = assertIs<ApiAuthorizationResult.Denied>(service.execute(command))
            assertEquals(403, result.statusCode)
            assertEquals(message, result.message)
        }
        every { subscriptions.findActiveByUserId(key.userId) } returns null
        assertEquals("No active subscription", assertIs<ApiAuthorizationResult.Denied>(service.execute(command)).message)
        verify { limiter wasNot Called }
    }

    @Test
    fun `active and trialing subscriptions reach the limiter after key and subscription checks`() {
        every { keys.findByKeyHash(any()) } returns key
        every { limiter.isAllowed(key.id, 60) } returns true
        for (status in listOf("active", "trialing")) {
            every { subscriptions.findActiveByUserId(key.userId) } returns subscription(status)
            val result = assertIs<ApiAuthorizationResult.Authorized>(service.execute(command))
            assertEquals(key.id, result.keyId)
            assertEquals(key.userId, result.userId)
        }
        verifyOrder {
            keys.findByKeyHash(any())
            subscriptions.findActiveByUserId(key.userId)
            limiter.isAllowed(key.id, 60)
        }
    }

    @Test
    fun `legacy key plan cannot raise subscription rate limit`() {
        every { keys.findByKeyHash(any()) } returns key.copy(rateLimitRpm = 200)
        every { subscriptions.findActiveByUserId(key.userId) } returns subscription("active")
        every { limiter.isAllowed(key.id, 60) } returns true
        assertIs<ApiAuthorizationResult.Authorized>(service.execute(command))
        verify(exactly = 0) { limiter.isAllowed(key.id, 200) }
    }

    @Test
    fun `rate rejection preserves status and metrics`() {
        every { keys.findByKeyHash(any()) } returns key
        every { subscriptions.findActiveByUserId(key.userId) } returns subscription("active")
        every { limiter.isAllowed(key.id, 60) } returns false
        val result = assertIs<ApiAuthorizationResult.Denied>(service.execute(command))
        assertEquals(429, result.statusCode)
        assertEquals("Rate limit exceeded", result.message)
        assertEquals(1.0, metrics.rateLimitRejections.count())
        assertEquals(1.0, metrics.authFailuresRateLimit.count())
    }
}
