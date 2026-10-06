package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyStore
import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class ShopifyCredentialCipher(private val config: ShopifyProperties) {
    private val random = SecureRandom()
    fun encrypt(value: String, store: ShopifyStore): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, nonce, store)
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String, store: ShopifyStore): String = try {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 28)
        String(cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, 12), store).doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    } catch (_: Exception) {
        throw ApiException(503, "Shopify credentials could not be decrypted; check the credential encryption key")
    }
    fun configured() {
        key()
    }
    private fun key(): SecretKeySpec {
        val bytes = runCatching { Base64.getDecoder().decode(config.credentialEncryptionKey) }.getOrNull()
        if (bytes?.size != 32) throw ApiException(503, "Configure shopify.credential-encryption-key with a base64-encoded 32-byte key")
        return SecretKeySpec(bytes, "AES")
    }
    private fun cipher(mode: Int, nonce: ByteArray, store: ShopifyStore) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key(), GCMParameterSpec(128, nonce))
        updateAAD("${store.connectionId}:${store.installedAt.toInstant()}".toByteArray(Charsets.UTF_8))
    }
}
