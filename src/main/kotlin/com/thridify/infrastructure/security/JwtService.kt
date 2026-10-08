package com.thridify.infrastructure.security

import com.thridify.domain.identity.TokenClient
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${omni3d.jwt.secret}") private val secret: String,
    @Value("\${omni3d.jwt.expiration-ms}") private val expirationMs: Long,
    environment: org.springframework.core.env.Environment,
) : TokenClient {

    init {
        require(secret.toByteArray().size >= 32 && !secret.startsWith("CHANGE_ME")) { "Configure a JWT_SECRET of at least 32 bytes" }
        require(!secret.startsWith("local-development-only-") || environment.activeProfiles.toSet() == setOf("local")) { "The development JWT secret requires only the local profile" }
    }
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    override fun generateToken(userId: UUID, email: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(Date(now.time + expirationMs))
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims? = try {
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    } catch (ex: Exception) {
        null
    }

    override fun subject(token: String): String? = validateToken(token)?.subject

    fun getUserIdFromToken(token: String): UUID? = validateToken(token)?.subject?.let { UUID.fromString(it) }
}
