package com.omni3d.application.service

import com.omni3d.shared.exception.ConflictException
import com.omni3d.shared.exception.UnauthorizedException
import com.omni3d.shared.metrics.AppMetrics
import com.omni3d.interfaces.rest.dto.AuthResponse
import com.omni3d.interfaces.rest.dto.LoginRequest
import com.omni3d.interfaces.rest.dto.RegisterRequest
import com.omni3d.infrastructure.persistence.UserRepository
import com.omni3d.infrastructure.security.JwtService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val metrics: AppMetrics
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    fun register(request: RegisterRequest): AuthResponse {
        log.info("Registration attempt [email={}]", request.email)

        if (userRepository.existsByEmail(request.email)) {
            log.warn("Registration rejected -- email already taken [email={}]", request.email)
            throw ConflictException("Email already registered")
        }

        val userId = UUID.randomUUID()
        val hash   = passwordEncoder.encode(request.password)
        userRepository.insert(userId, request.email, hash!!, request.name)

        val token = jwtService.generateToken(userId, request.email)
        metrics.usersRegistered.increment()
        MDC.put("userId", userId.toString())
        log.info("User registered successfully [userId={}, email={}]", userId, request.email)
        MDC.remove("userId")

        return AuthResponse(token = token, userId = userId, email = request.email, isAdmin = false)
    }

    fun login(request: LoginRequest): AuthResponse {
        log.debug("Login attempt [email={}]", request.email)

        val user = userRepository.findByEmail(request.email)
        if (user == null) {
            log.warn("Login failed -- user not found [email={}]", request.email)
            metrics.usersLoginFailed.increment()
            throw UnauthorizedException("Invalid credentials")
        }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            log.warn("Login failed -- wrong password [email={}]", request.email)
            metrics.usersLoginFailed.increment()
            throw UnauthorizedException("Invalid credentials")
        }

        val token = jwtService.generateToken(user.id, user.email)
        metrics.usersLoginSuccess.increment()
        log.info("User logged in [userId={}, email={}]", user.id, user.email)

        return AuthResponse(token = token, userId = user.id, email = user.email, isAdmin = user.isAdmin)
    }
}
