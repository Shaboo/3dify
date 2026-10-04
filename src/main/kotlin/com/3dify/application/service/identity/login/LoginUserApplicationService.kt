package com.`3dify`.application.service.identity.login

import com.`3dify`.application.service.identity.AuthResult
import com.`3dify`.domain.identity.IdentityPolicy
import com.`3dify`.domain.identity.PasswordHasher
import com.`3dify`.domain.identity.TokenClient
import com.`3dify`.domain.identity.UserRepository
import com.`3dify`.shared.exception.UnauthorizedException
import com.`3dify`.shared.metrics.AppMetrics
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
        log.info("User logged in [userId={}, email={}]", user.id, user.email)
        return AuthResult(token, user.id, user.email, user.isAdmin)
    }
}
