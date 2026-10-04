package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifySession
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.shared.exception.UnauthorizedException
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.net.URI
import javax.crypto.spec.SecretKeySpec

@Component
class ShopifyIdTokenVerifier(private val config: ShopifyProperties) : ShopifySessionVerifier {
    override fun verify(token: String): ShopifySession {
        config.requireEnabled()
        try {
            val jwt = Jwts.parser().verifyWith(SecretKeySpec(config.clientSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")).build().parseSignedClaims(token)
            val claims = jwt.payload
            if (jwt.header.algorithm != "HS256" || config.clientId !in claims.audience || claims.expiration == null || claims.notBefore == null) throw IllegalArgumentException()
            val dest = URI(claims.get("dest", String::class.java))
            val issuer = URI(claims.issuer)
            val shop = dest.host ?: throw IllegalArgumentException()
            if (!Regex("[a-z0-9][a-z0-9-]*[.]myshopify[.]com").matches(shop)) throw IllegalArgumentException()
            if (dest.scheme != "https" || dest.port != -1 || dest.rawUserInfo != null || dest.rawQuery != null || dest.rawFragment != null || dest.path !in listOf("", "/")) throw IllegalArgumentException()
            if (issuer.scheme != "https" || issuer.host != shop || issuer.port != -1 || issuer.rawUserInfo != null || issuer.path != "/admin" || issuer.rawQuery != null || issuer.rawFragment != null) throw IllegalArgumentException()
            val staff = claims.subject
            if (staff.isNullOrBlank()) throw IllegalArgumentException()
            return ShopifySession(shop, staff)
        } catch (_: Exception) {
            throw UnauthorizedException("Invalid or expired Shopify ID token")
        }
    }
}
