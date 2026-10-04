package com.`3dify`.application.service.identity.register

import com.`3dify`.application.service.identity.AuthResult
import com.`3dify`.domain.identity.IdentityPolicy
import com.`3dify`.domain.identity.PasswordHasher
import com.`3dify`.domain.identity.TokenClient
import com.`3dify`.domain.identity.UserRepository
import com.`3dify`.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RegisterUserApplicationService(
    private val users: UserRepository,
    private val passwords: PasswordHasher,
    private val tokens: TokenClient,
    private val metrics: AppMetrics,
    private val policy: IdentityPolicy,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: RegisterUserCommand): AuthResult {
        policy.ensureEmailAvailable(users.existsByEmail(command.email))
        val id = UUID.randomUUID()
        val hash = passwords.encode(command.password)
        users.insert(id, command.email, hash, command.name)
        val token = tokens.generateToken(id, command.email)
        metrics.usersRegistered.increment()
        MDC.put("userId", id.toString())
        log.info("User registered successfully [userId={}, email={}]", id, command.email)
        MDC.remove("userId")
        return AuthResult(token, id, command.email, false)
    }
}
