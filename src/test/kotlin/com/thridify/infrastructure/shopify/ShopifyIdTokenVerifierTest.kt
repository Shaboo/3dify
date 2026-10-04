package com.thridify.infrastructure.shopify

import com.thridify.shared.exception.UnauthorizedException
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Date
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

class ShopifyIdTokenVerifierTest {
    private val config = ShopifyProperties(enabled = true, clientId = "client-id", clientSecret = "0123456789abcdef0123456789abcdef")
    private val verifier = ShopifyIdTokenVerifier(config)

    private fun token(audience: String = config.clientId, dest: String = "https://alpha.myshopify.com", issuer: String = "https://alpha.myshopify.com/admin", expiry: Instant = Instant.now().plusSeconds(60), notBefore: Instant = Instant.now().minusSeconds(1), secret: String = config.clientSecret): String = Jwts.builder()
        .audience().add(audience).and().subject("42").issuer(issuer).claim("dest", dest)
        .expiration(Date.from(expiry)).notBefore(Date.from(notBefore)).issuedAt(Date.from(Instant.now()))
        .signWith(SecretKeySpec(secret.toByteArray(), "HmacSHA256"), Jwts.SIG.HS256).compact()

    @Test
    fun `valid token resolves shop and staff without accepting browser supplied identity`() {
        val session = verifier.verify(token())
        assertEquals("alpha.myshopify.com", session.shopDomain)
        assertEquals("42", session.staffId)
    }

    @Test
    fun `rejects signature audience expiry and future not-before`() {
        for (invalid in listOf(token(secret = "fedcba9876543210fedcba9876543210"), token(audience = "another-app"), token(expiry = Instant.now().minusSeconds(1)), token(notBefore = Instant.now().plusSeconds(60)))) {
            assertThrows<UnauthorizedException> { verifier.verify(invalid) }
        }
    }

    @Test
    fun `rejects mismatched or unsafe shop origins before any token exchange`() {
        for (invalid in listOf(token(dest = "https://attacker.example"), token(issuer = "https://beta.myshopify.com/admin"), token(dest = "https://alpha.myshopify.com:444"), token(dest = "https://alpha.myshopify.com@attacker.example"), token(dest = "http://alpha.myshopify.com"))) {
            assertThrows<UnauthorizedException> { verifier.verify(invalid) }
        }
    }

    @Test
    fun `required time claims and algorithm are enforced`() {
        val missingExpiry = Jwts.builder().audience().add(config.clientId).and().subject("42").issuer("https://alpha.myshopify.com/admin").claim("dest", "https://alpha.myshopify.com").signWith(SecretKeySpec(config.clientSecret.toByteArray(), "HmacSHA256"), Jwts.SIG.HS256).compact()
        val differentAlgorithm = Jwts.builder().audience().add(config.clientId).and().subject("42").issuer("https://alpha.myshopify.com/admin").claim("dest", "https://alpha.myshopify.com").expiration(Date.from(Instant.now().plusSeconds(60))).notBefore(Date.from(Instant.now().minusSeconds(1))).signWith(SecretKeySpec((config.clientSecret + config.clientSecret).toByteArray(), "HmacSHA512"), Jwts.SIG.HS512).compact()
        assertThrows<UnauthorizedException> { verifier.verify(missingExpiry) }
        assertThrows<UnauthorizedException> { verifier.verify(differentAlgorithm) }
    }
}
