package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyStore
import com.thridify.shared.exception.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ShopifyCredentialCipherTest {
    private val config = ShopifyProperties(credentialEncryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
    private val cipher = ShopifyCredentialCipher(config)
    private val store = ShopifyStore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "gid://shopify/Shop/1", "test.myshopify.com", true, OffsetDateTime.now())

    @Test
    fun `encrypted tokens use random nonces and bind to the store installation`() {
        val one = cipher.encrypt("secret-token", store)
        val two = cipher.encrypt("secret-token", store)
        assertNotEquals(one, two)
        assertEquals("secret-token", cipher.decrypt(one, store))
        assertThrows<ApiException> { cipher.decrypt(one, store.copy(connectionId = UUID.randomUUID())) }
        assertThrows<ApiException> { cipher.decrypt(one, store.copy(installedAt = store.installedAt.plusSeconds(1))) }
        val bytes = Base64.getDecoder().decode(one).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows<ApiException> { cipher.decrypt(Base64.getEncoder().encodeToString(bytes), store) }
    }

    @Test
    fun `missing encryption key fails without writing plaintext credentials`() {
        assertThrows<ApiException> { ShopifyCredentialCipher(ShopifyProperties()).encrypt("secret", store) }
    }
}
