package com.thridify.infrastructure.persistence

import com.thridify.IntegrationTestBase
import com.thridify.domain.access.RequestRateLimiter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimitPlanChangeTest : IntegrationTestBase() {
    @Autowired private lateinit var limiter: RequestRateLimiter

    @Test
    fun `a lower subscribed rate does not reuse a previously higher bucket`() {
        val id = UUID.randomUUID()
        assertTrue(limiter.isAllowed(id, 200))
        assertTrue(limiter.isAllowed(id, 1))
        assertFalse(limiter.isAllowed(id, 1))
    }
}
