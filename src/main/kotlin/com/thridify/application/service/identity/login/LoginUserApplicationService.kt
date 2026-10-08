package com.thridify.application.service.identity.login

import com.thridify.application.service.identity.AuthResult
import com.thridify.domain.identity.IdentityPolicy
import com.thridify.domain.identity.PasswordHasher
import com.thridify.domain.identity.TokenClient
import com.thridify.domain.identity.UserRepository
import com.thridify.shared.exception.UnauthorizedException
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LoginUserApplicationService(
    private val users: UserRepository,
    private val passwords: PasswordHasher,
    private val tokens: TokenClient,
    private val metrics: AppMetrics,
    private val policy: IdentityPolicy,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: LoginUserCommand): AuthResult {
        policy.credentials(command.email, command.password)
        val user = try {
            val found = policy.requireUser(users.findByEmail(command.email))
            policy.ensurePasswordMatches(passwords.matches(command.password, found.passwordHash))
            found
        } catch (ex: UnauthorizedException) {
            metrics.usersLoginFailed.increment()
            throw ex
        }
        val token = tokens.generateToken(user.id, user.email)
        metrics.usersLoginSuccess.increment()
        log.info("User logged in [userId={}]", user.id)
        return AuthResult(token, user.id, user.email, user.isAdmin)
    }
}
