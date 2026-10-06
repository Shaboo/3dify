package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.domain.shopify.ShopifyShop
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.shared.exception.ApiException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ShopifyOfflineTokensTest : IntegrationTestBase() {
    @Autowired private lateinit var stores: ShopifyStoreRepository
    private val mapper = jacksonObjectMapper()
    private val http: ShopifyHttpClient = mockk()
    private val config = ShopifyProperties(enabled = true, clientId = "test-client", clientSecret = "test-secret", credentialEncryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
    private val cipher = ShopifyCredentialCipher(config)

    @BeforeEach
    fun setup() {
        resetDatabase()
    }
    private fun response(access: String = "offline-secret", refresh: String = "refresh-secret") = mapper.readTree("""{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"refresh_token_expires_in":7776000,"scope":"write_products"}""")
    private fun tokens() = ShopifyOfflineTokens(config, http, dsl, cipher)

    @Test
    fun `expiring credentials are encrypted reused and refreshed without a merchant session`() {
        val store = stores.connect(ShopifyShop("gid://shopify/Shop/1", "token-test.myshopify.com", "Test"))
        every { http.post(any(), any(), any()) } answers {
            val form = thirdArg<String>()
            if (form.contains("grant_type=refresh_token")) {
                assertFalse(form.contains("subject_token="))
                response("renewed-secret", "renewed-refresh")
            } else {
                assertFalse(form.contains("online-access-token"))
                kotlin.test.assertTrue(form.contains("expiring=1"))
                response()
            }
        }
        assertEquals("offline-secret", tokens().accessToken(store, "merchant-session"))
        assertEquals("offline-secret", tokens().accessToken(store))
        verify(exactly = 1) { http.post(any(), any(), any()) }
        val saved = dsl.fetchOne("SELECT access_ciphertext, refresh_ciphertext FROM shopify_offline_credentials")!!
        assertFalse(saved.toString().contains("offline-secret"))
        assertFalse(saved.toString().contains("refresh-secret"))
        dsl.execute("UPDATE shopify_offline_credentials SET access_expires_at = now() - interval '1 minute'")
        assertEquals("renewed-secret", tokens().accessToken(store))
        val updated = dsl.fetchOne("SELECT access_ciphertext, refresh_ciphertext FROM shopify_offline_credentials")!!
        assertEquals("renewed-secret", cipher.decrypt(updated.get("access_ciphertext", String::class.java)!!, store))
        assertEquals("renewed-refresh", cipher.decrypt(updated.get("refresh_ciphertext", String::class.java)!!, store))
        verify(exactly = 2) { http.post(any(), any(), any()) }
    }

    @Test
    fun `an expired refresh token can be reacquired with a current merchant session`() {
        val store = stores.connect(ShopifyShop("gid://shopify/Shop/1", "token-test.myshopify.com", "Test"))
        every { http.post(any(), any(), any()) } returns response()
        tokens().accessToken(store, "old-session")
        dsl.execute("UPDATE shopify_offline_credentials SET access_expires_at = now() - interval '1 minute'")
        every { http.post(any(), any(), any()) } answers {
            if (thirdArg<String>().contains("grant_type=refresh_token")) throw ApiException(401, "expired")
            response("reacquired", "new-refresh")
        }
        assertEquals("reacquired", tokens().accessToken(store, "fresh-session"))
        tokens().invalidate(store, "old-secret")
        assertEquals(1, dsl.fetchOne("SELECT count(*) AS total FROM shopify_offline_credentials")!!.get("total", Int::class.java))
        tokens().invalidate(store, "reacquired")
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM shopify_offline_credentials")!!.get("total", Int::class.java))
    }

    @Test
    fun `disconnected stores and concurrent refresh attempts cannot mint credentials`() {
        val store = stores.connect(ShopifyShop("gid://shopify/Shop/1", "token-test.myshopify.com", "Test"))
        val lock = "shopify-offline:${store.connectionId}"
        dsl.connectionResult { connection ->
            val db = DSL.using(connection, SQLDialect.POSTGRES)
            db.fetch("SELECT pg_advisory_lock(hashtextextended(?, 0))", lock)
            try {
                assertEquals(503, assertThrows<ApiException> { tokens().accessToken(store, "id") }.statusCode)
            } finally {
                db.fetch("SELECT pg_advisory_unlock(hashtextextended(?, 0))", lock)
            }
        }
        dsl.execute("UPDATE platform_connections SET status = 'disconnected'")
        assertEquals(403, assertThrows<ApiException> { tokens().accessToken(store, "id") }.statusCode)
        verify(exactly = 0) { http.post(any(), any(), any()) }
    }

    @Test
    fun `uninstall during exchange cannot resurrect a credential row`() {
        val store = stores.connect(ShopifyShop("gid://shopify/Shop/1", "token-test.myshopify.com", "Test"))
        every { http.post(any(), any(), any()) } answers {
            dsl.execute("UPDATE platform_connections SET status = 'disconnected'")
            response()
        }
        assertEquals(403, assertThrows<ApiException> { tokens().accessToken(store, "id") }.statusCode)
        assertEquals(0, dsl.fetchOne("SELECT count(*) AS total FROM shopify_offline_credentials")!!.get("total", Int::class.java))
    }
}
