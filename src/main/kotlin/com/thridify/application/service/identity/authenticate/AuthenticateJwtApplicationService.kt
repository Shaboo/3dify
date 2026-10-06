package com.thridify.application.service.identity.authenticate

import com.thridify.domain.identity.TokenClient
import com.thridify.domain.identity.UserRepository
import com.thridify.shared.metrics.AppMetrics
import org.springframework.stereotype.Service
import java.util.UUID

data class JwtIdentityResult(val subject: String, val isAdmin: Boolean)

@Service
class AuthenticateJwtApplicationService(private val tokens: TokenClient, private val users: UserRepository, private val metrics: AppMetrics) {
    fun execute(query: AuthenticateJwtQuery): JwtIdentityResult? {
        val subject = tokens.subject(query.token)
        metrics.recordJwtValidation(subject != null)
        if (subject == null) return null
        val user = users.findById(UUID.fromString(subject))
        return JwtIdentityResult(subject, user?.isAdmin ?: false)
    }
}
