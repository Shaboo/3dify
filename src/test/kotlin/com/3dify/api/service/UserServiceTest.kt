package com.omni3d.api.service

import com.omni3d.api.domain.UserEntity
import com.omni3d.api.exception.ConflictException
import com.omni3d.api.exception.UnauthorizedException
import com.omni3d.api.metrics.AppMetrics
import com.omni3d.api.model.dto.LoginRequest
import com.omni3d.api.model.dto.RegisterRequest
import com.omni3d.api.repository.UserRepository
import com.omni3d.api.security.JwtService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class UserServiceTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder = BCryptPasswordEncoder()
    private val jwtService: JwtService = mockk()
    private val metrics = AppMetrics(SimpleMeterRegistry())
    private val service = UserService(userRepository, passwordEncoder, jwtService, metrics)

    private fun userEntity(
        id: UUID = UUID.randomUUID(),
        email: String = "user@example.com",
        passwordHash: String = passwordEncoder.encode("correct-password") ?: error("encoder returned null"),
        isAdmin: Boolean = false
    ) = UserEntity(id = id, email = email, passwordHash = passwordHash, name = null, isAdmin = isAdmin)

    @Test
    fun `register succeeds when email is unique`() {
        every { userRepository.existsByEmail("new@example.com") } returns false
        every { userRepository.insert(any(), "new@example.com", any(), "Alice") } returns Unit
        every { jwtService.generateToken(any(), "new@example.com") } returns "jwt-token"

        val response = service.register(
            RegisterRequest(email = "new@example.com", password = "secret", name = "Alice")
        )

        assertEquals("jwt-token", response.token)
        assertEquals("new@example.com", response.email)
        assertFalse(response.isAdmin)
        verify(exactly = 1) { userRepository.insert(any(), "new@example.com", any(), "Alice") }
    }

    @Test
    fun `register throws ConflictException when email is taken`() {
        every { userRepository.existsByEmail("dup@example.com") } returns true

        assertThrows<ConflictException> {
            service.register(RegisterRequest(email = "dup@example.com", password = "pass", name = "Dup"))
        }
        verify(exactly = 0) { userRepository.insert(any(), any(), any(), any()) }
    }

    @Test
    fun `login returns token for valid credentials`() {
        val userId = UUID.randomUUID()
        val hash = passwordEncoder.encode("correct-password") ?: error("encoder returned null")
        val user = userEntity(id = userId, email = "user@example.com", passwordHash = hash)
        every { userRepository.findByEmail("user@example.com") } returns user
        every { jwtService.generateToken(userId, "user@example.com") } returns "valid-token"

        val response = service.login(LoginRequest(email = "user@example.com", password = "correct-password"))

        assertEquals("valid-token", response.token)
        assertEquals("user@example.com", response.email)
        assertFalse(response.isAdmin)
    }

    @Test
    fun `login throws UnauthorizedException when user not found`() {
        every { userRepository.findByEmail("ghost@example.com") } returns null

        assertThrows<UnauthorizedException> {
            service.login(LoginRequest(email = "ghost@example.com", password = "pass"))
        }
    }

    @Test
    fun `login throws UnauthorizedException when password is wrong`() {
        val user = userEntity(passwordHash = passwordEncoder.encode("correct") ?: error("encoder returned null"))
        every { userRepository.findByEmail("user@example.com") } returns user

        assertThrows<UnauthorizedException> {
            service.login(LoginRequest(email = "user@example.com", password = "wrong"))
        }
    }
}
