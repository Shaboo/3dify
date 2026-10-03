package com.omni3d.application.service

import com.omni3d.domain.ApiKeyAuthEntity
import com.omni3d.domain.PlanEntity
import com.omni3d.shared.exception.NotFoundException
import com.omni3d.shared.metrics.AppMetrics
import com.omni3d.infrastructure.persistence.ApiKeyRepository
import com.omni3d.infrastructure.persistence.PlanRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyServiceTest {

    private val apiKeyRepository: ApiKeyRepository = mockk()
    private val planRepository: PlanRepository = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val service = ApiKeyService(apiKeyRepository, planRepository, metrics)

    private fun plan(
        id: UUID = UUID.randomUUID(),
        name: String = "free",
        rateLimitRpm: Int = 60
    ) = PlanEntity(
        id            = id,
        name          = name,
        displayName   = "Free",
        description   = null,
        rateLimitRpm  = rateLimitRpm,
        monthlyQuota  = 100,
        priceCents    = 0,
        currency      = "usd",
        stripePriceId = null,
        isActive      = true,
        sortOrder     = 0
    )

    private fun authEntity(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        isActive: Boolean = true,
        rateLimitRpm: Int = 60
    ) = ApiKeyAuthEntity(id = id, userId = userId, isActive = isActive, rateLimitRpm = rateLimitRpm)

    @Test
    fun `generateKey returns raw key starting with omni_pk_`() {
        val planId = UUID.randomUUID()
        every { planRepository.findByName("free") } returns plan(id = planId)
        every { apiKeyRepository.insert(any(), any(), planId, any(), any(), null) } just Runs

        val response = service.generateKey(UUID.randomUUID(), null, "free")

        assertTrue(response.key.startsWith("omni_pk_"))
        assertEquals("free", response.planName)
    }

    @Test
    fun `generateKey throws NotFoundException when plan does not exist`() {
        every { planRepository.findByName("nonexistent") } returns null

        assertThrows<NotFoundException> {
            service.generateKey(UUID.randomUUID(), null, "nonexistent")
        }
        verify(exactly = 0) { apiKeyRepository.insert(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `validateRawKey returns null when key does not start with omni_pk_`() {
        assertNull(service.validateRawKey("invalid_key_format"))
    }

    @Test
    fun `validateRawKey returns null when hash not found in DB`() {
        every { apiKeyRepository.findByKeyHash(any()) } returns null
        assertNull(service.validateRawKey("omni_pk_somefakekey1234567890123456789012345678"))
    }

    @Test
    fun `validateRawKey returns null when key is inactive`() {
        every { apiKeyRepository.findByKeyHash(any()) } returns authEntity(isActive = false)
        assertNull(service.validateRawKey("omni_pk_somefakekey1234567890123456789012345678"))
    }

    @Test
    fun `validateRawKey returns ValidatedApiKey for active key`() {
        val keyId  = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { apiKeyRepository.findByKeyHash(any()) } returns authEntity(id = keyId, userId = userId, rateLimitRpm = 60)

        val result = service.validateRawKey("omni_pk_somefakekey1234567890123456789012345678")

        assertNotNull(result)
        assertEquals(keyId, result.id)
        assertEquals(userId, result.userId)
        assertEquals(60, result.rateLimitRpm)
    }

    @Test
    fun `revokeKey throws NotFoundException when no rows updated`() {
        every { apiKeyRepository.revoke(any(), any()) } returns 0

        assertThrows<NotFoundException> {
            service.revokeKey(UUID.randomUUID(), UUID.randomUUID())
        }
    }

    @Test
    fun `revokeKey succeeds when row is updated`() {
        every { apiKeyRepository.revoke(any(), any()) } returns 1
        service.revokeKey(UUID.randomUUID(), UUID.randomUUID())
        verify(exactly = 1) { apiKeyRepository.revoke(any(), any()) }
    }
}
