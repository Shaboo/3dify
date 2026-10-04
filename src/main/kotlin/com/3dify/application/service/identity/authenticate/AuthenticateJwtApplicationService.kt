package com.`3dify`.application.service.identity.authenticate

import com.`3dify`.domain.identity.TokenClient
import com.`3dify`.domain.identity.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

data class JwtIdentityResult(val subject: String, val isAdmin: Boolean)

@Service
class AuthenticateJwtApplicationService(private val tokens: TokenClient, private val users: UserRepository) {
    fun execute(query: AuthenticateJwtQuery): JwtIdentityResult? {
        val subject = tokens.subject(query.token) ?: return null
        val user = users.findById(UUID.fromString(subject))
        return JwtIdentityResult(subject, user?.isAdmin ?: false)
    }
}
