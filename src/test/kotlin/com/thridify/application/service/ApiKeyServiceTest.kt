package com.thridify.application.service

import com.thridify.application.service.access.authorize.ApiAuthorizationResult
import com.thridify.application.service.access.authorize.AuthorizeApiRequestApplicationService
import com.thridify.application.service.access.authorize.AuthorizeApiRequestCommand
import com.thridify.application.service.apikey.create.CreateApiKeyApplicationService
import com.thridify.application.service.apikey.create.CreateApiKeyCommand
import com.thridify.application.service.apikey.revoke.RevokeApiKeyApplicationService
import com.thridify.application.service.apikey.revoke.RevokeApiKeyCommand
import com.thridify.domain.access.RequestRateLimiter
import com.thridify.domain.apikey.ApiKeyAuthEntity
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.domain.plan.PlanEntity
import com.thridify.domain.plan.PlanRepository
import com.thridify.domain.subscription.SubscriptionRepository
import com.thridify.shared.exception.NotFoundException
import com.thridify.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiKeyServiceTest {

    private val apiKeyRepository: ApiKeyRepository = mockk()
    private val planRepository: PlanRepository = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val policy = com.thridify.domain.access.ApiKeyPolicy()
    private val revoke = RevokeApiKeyApplicationService(apiKeyRepository, metrics, policy)
    private val subscriptions: SubscriptionRepository = mockk()
    private val create = CreateApiKeyApplicationService(apiKeyRepository, planRepository, metrics, policy, subscriptions)
    private val limiter: RequestRateLimiter = mockk()
    private val authorize = AuthorizeApiRequestApplicationService(apiKeyRepository, subscriptions, limiter, policy, metrics)

    private fun plan(
        id: UUID = UUID.randomUUID(),
        name: String = "free",
        rateLimitRpm: Int = 60,
    ) = PlanEntity(
        id = id,
        name = name,
        displayName = "Free",
        description = null,
        rateLimitRpm = rateLimitRpm,
        monthlyQuota = 100,
        priceCents = 0,
        currency = "usd",
        stripePriceId = null,
        isActive = true,
        sortOrder = 0,
    )

    private fun authEntity(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        isActive: Boolean = true,
        rateLimitRpm: Int = 60,
    ) = ApiKeyAuthEntity(id = id, userId = userId, isActive = isActive, rateLimitRpm = rateLimitRpm)

    @Test
    fun `deactivated plans cannot issue new keys`() {
        every { planRepository.findByName("free") } returns plan().copy(isActive = false)
        every { subscriptions.findActiveByUserId(any()) } returns null
        assertThrows<NotFoundException> { create.execute(CreateApiKeyCommand(UUID.randomUUID(), null, "free")) }
        verify(exactly = 0) { apiKeyRepository.insert(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `generateKey returns raw key starting with omni_pk_`() {
        val planId = UUID.randomUUID()
        every { subscriptions.findActiveByUserId(any()) } returns null
        every { planRepository.findByName("free") } returns plan(id = planId)
        every { apiKeyRepository.insert(any(), any(), planId, any(), any(), null) } just Runs

        val response = create.execute(CreateApiKeyCommand(UUID.randomUUID(), null, "free"))

        assertTrue(response.key.startsWith("omni_pk_"))
        assertEquals("free", response.planName)
    }

    @Test
    fun `generateKey throws NotFoundException when plan does not exist`() {
        every { planRepository.findByName("nonexistent") } returns null

        assertThrows<NotFoundException> {
            create.execute(CreateApiKeyCommand(UUID.randomUUID(), null, "nonexistent"))
        }
        verify(exactly = 0) { apiKeyRepository.insert(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `validateRawKey returns null when key does not start with omni_pk_`() {
        assertIs<ApiAuthorizationResult.Denied>(authorize.execute(AuthorizeApiRequestCommand("invalid_key_format")))
    }

    @Test
    fun `validateRawKey returns null when hash not found in DB`() {
        every { apiKeyRepository.findByKeyHash(any()) } returns null
        assertIs<ApiAuthorizationResult.Denied>(authorize.execute(AuthorizeApiRequestCommand("omni_pk_somefakekey1234567890123456789012345678")))
    }

    @Test
    fun `validateRawKey returns null when key is inactive`() {
        every { apiKeyRepository.findByKeyHash(any()) } returns authEntity(isActive = false)
        assertIs<ApiAuthorizationResult.Denied>(authorize.execute(AuthorizeApiRequestCommand("omni_pk_somefakekey1234567890123456789012345678")))
    }

    @Test
    fun `validateRawKey returns ValidatedApiKey for active key`() {
        val keyId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { apiKeyRepository.findByKeyHash(any()) } returns authEntity(id = keyId, userId = userId, rateLimitRpm = 60)

        val subscription: com.thridify.domain.subscription.SubscriptionWithPlanEntity = mockk()
        every { subscription.status } returns "active"
        every { subscription.planRateLimitRpm } returns 60
        every { subscriptions.findActiveByUserId(userId) } returns subscription
        every { limiter.isAllowed(keyId, 60) } returns true
        val result = authorize.execute(AuthorizeApiRequestCommand("omni_pk_somefakekey1234567890123456789012345678"))

        assertIs<ApiAuthorizationResult.Authorized>(result)
        assertEquals(keyId, result.keyId)
        assertEquals(userId, result.userId)
        verify(exactly = 1) { limiter.isAllowed(keyId, 60) }
    }

    @Test
    fun `revokeKey throws NotFoundException when no rows updated`() {
        every { apiKeyRepository.revoke(any(), any()) } returns 0

        assertThrows<NotFoundException> {
            revoke.execute(RevokeApiKeyCommand(UUID.randomUUID(), UUID.randomUUID()))
        }
    }

    @Test
    fun `revokeKey succeeds when row is updated`() {
        every { apiKeyRepository.revoke(any(), any()) } returns 1
        revoke.execute(RevokeApiKeyCommand(UUID.randomUUID(), UUID.randomUUID()))
        verify(exactly = 1) { apiKeyRepository.revoke(any(), any()) }
    }
}
