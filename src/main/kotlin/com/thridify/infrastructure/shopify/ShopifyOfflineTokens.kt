package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyStore
import com.thridify.shared.exception.ApiException
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.OffsetDateTime

@Component
class ShopifyOfflineTokens(private val config: ShopifyProperties, private val http: ShopifyHttpClient, private val dsl: DSLContext, private val cipher: ShopifyCredentialCipher) {
    fun invalidate(store: ShopifyStore, accessToken: String) {
        val saved = dsl.fetchOne("SELECT access_ciphertext FROM shopify_offline_credentials WHERE connection_id = ? AND installed_at = ?", store.connectionId, Timestamp.from(store.installedAt.toInstant())) ?: return
        val ciphertext = saved.get("access_ciphertext", String::class.java)!!
        if (cipher.decrypt(ciphertext, store) == accessToken) dsl.execute("DELETE FROM shopify_offline_credentials WHERE connection_id = ? AND access_ciphertext = ?", store.connectionId, ciphertext)
    }
    fun accessToken(store: ShopifyStore, idToken: String? = null): String {
        config.requireEnabled()
        cipher.configured()
        return dsl.connectionResult { connection ->
            val db = DSL.using(connection, SQLDialect.POSTGRES)
            // A session lock serializes token exchange and refresh across all instances.
            // The connection is in autocommit mode: no database transaction spans OAuth HTTP.
            val lock = "shopify-offline:${store.connectionId}"
            if (db.fetchOne("SELECT pg_try_advisory_lock(hashtextextended(?, 0))", lock)?.get(0, Boolean::class.java) != true) throw ApiException(503, "Shopify credentials are being refreshed; retry shortly")
            try {
                val installedAt = Timestamp.from(store.installedAt.toInstant())
                if (db.fetchOne("SELECT id FROM platform_connections WHERE id = ? AND status = 'connected' AND installed_at = ?", store.connectionId, installedAt) == null) throw ApiException(403, "The Shopify app is disconnected")
                val saved = db.fetchOne("SELECT * FROM shopify_offline_credentials WHERE connection_id = ? AND installed_at = ?", store.connectionId, installedAt)
                val now = OffsetDateTime.now()
                val accessExpiry = saved?.get("access_expires_at", OffsetDateTime::class.java)
                if (accessExpiry?.isAfter(now.plusSeconds(120)) == true) return@connectionResult cipher.decrypt(saved.get("access_ciphertext", String::class.java)!!, store)
                val refreshExpiry = saved?.get("refresh_expires_at", OffsetDateTime::class.java)
                val form = mutableMapOf("client_id" to config.clientId, "client_secret" to config.clientSecret)
                if (refreshExpiry?.isAfter(now.plusSeconds(30)) == true) {
                    form["grant_type"] = "refresh_token"
                    form["refresh_token"] = cipher.decrypt(saved.get("refresh_ciphertext", String::class.java)!!, store)
                } else {
                    if (idToken == null) throw ApiException(401, "Reopen the app to authorize background product attachment")
                    form["grant_type"] = "urn:ietf:params:oauth:grant-type:token-exchange"
                    form["subject_token"] = idToken
                    form["subject_token_type"] = "urn:ietf:params:oauth:token-type:id_token"
                    form["requested_token_type"] = "urn:shopify:params:oauth:token-type:offline-access-token"
                    form["expiring"] = "1"
                }
                val payload = form.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
                val result = try {
                    http.post("https://${store.shopDomain}/admin/oauth/access_token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), payload)
                } catch (ex: ApiException) {
                    if (ex.statusCode != 401 || form["grant_type"] != "refresh_token") throw ex
                    db.execute("DELETE FROM shopify_offline_credentials WHERE connection_id = ? AND installed_at = ?", store.connectionId, installedAt)
                    if (idToken == null) throw ApiException(401, "Reopen the app to authorize background product attachment")
                    val exchange = mapOf("client_id" to config.clientId, "client_secret" to config.clientSecret, "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange", "subject_token" to idToken, "subject_token_type" to "urn:ietf:params:oauth:token-type:id_token", "requested_token_type" to "urn:shopify:params:oauth:token-type:offline-access-token", "expiring" to "1")
                    val retryPayload = exchange.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
                    http.post("https://${store.shopDomain}/admin/oauth/access_token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), retryPayload)
                }
                val access = result.path("access_token").asText()
                val refresh = result.path("refresh_token").asText()
                val accessSeconds = result.path("expires_in").asLong()
                val refreshSeconds = result.path("refresh_token_expires_in").asLong()
                if (access.isBlank() || refresh.isBlank() || accessSeconds <= 0 || refreshSeconds <= 0 || "write_products" !in result.path("scope").asText().split(',')) throw ApiException(502, "Shopify did not grant expiring offline product access")
                val changed = db.execute(
                    """INSERT INTO shopify_offline_credentials(connection_id, installed_at, access_ciphertext, refresh_ciphertext, access_expires_at, refresh_expires_at)
                    SELECT id, installed_at, ?, ?, ?, ? FROM platform_connections WHERE id = ? AND status = 'connected' AND installed_at = ?
                    ON CONFLICT(connection_id) DO UPDATE SET installed_at = EXCLUDED.installed_at, access_ciphertext = EXCLUDED.access_ciphertext, refresh_ciphertext = EXCLUDED.refresh_ciphertext,
                    access_expires_at = EXCLUDED.access_expires_at, refresh_expires_at = EXCLUDED.refresh_expires_at, updated_at = now()""",
                    cipher.encrypt(access, store),
                    cipher.encrypt(refresh, store),
                    Timestamp.from(now.plusSeconds(accessSeconds).toInstant()),
                    Timestamp.from(now.plusSeconds(refreshSeconds).toInstant()),
                    store.connectionId,
                    installedAt,
                )
                if (changed != 1) throw ApiException(403, "The Shopify app was disconnected during authorization")
                access
            } finally {
                db.fetch("SELECT pg_advisory_unlock(hashtextextended(?, 0))", lock)
            }
        }
    }
}
