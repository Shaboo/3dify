package com.thridify.infrastructure.security

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtServiceTest {
    @Test
    fun `missing placeholder and development production keys fail startup`() {
        for (secret in listOf("", "short", "CHANGE_ME_IN_PRODUCTION_minimum_32_chars_long_secret_key_here!", "local-development-only-jwt-secret-do-not-deploy")) {
            assertFailsWith<IllegalArgumentException> { JwtService(secret, 60000, MockEnvironment()) }
        }
        assertFailsWith<IllegalArgumentException> {
            JwtService("local-development-only-jwt-secret-do-not-deploy", 60000, MockEnvironment().withProperty("spring.profiles.active", "local,prod"))
        }
    }

    @Test
    fun `configured key signs and validates tokens`() {
        val service = JwtService("configured-secret-with-at-least-thirty-two-bytes", 60000, MockEnvironment())
        val id = UUID.randomUUID()
        assertEquals(id.toString(), service.subject(service.generateToken(id, "owner@example.com")))
    }
}
