package com.`3dify`.infrastructure.security

import com.`3dify`.domain.identity.TokenClient
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
) : TokenClient {

    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

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
