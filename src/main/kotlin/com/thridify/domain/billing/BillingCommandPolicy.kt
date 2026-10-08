package com.thridify.domain.billing
import com.thridify.shared.exception.ConflictException
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.OffsetDateTime
@Component
class BillingCommandPolicy {
    fun fingerprint(vararg values: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach {
            val bytes = it?.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
            bytes?.let(digest::update)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun ensureSameRequest(record: BillingCommandRecord, fingerprint: String) {
        if (record.fingerprint != fingerprint) throw ConflictException("Idempotency key was already used for a different billing request")
    }
    fun ensureProviderRetrySafe(record: BillingCommandRecord, now: OffsetDateTime = OffsetDateTime.now()) {
        // shortcut: uncertain requests older than Stripe's retention window require manual reconciliation.
        if (record.providerResult == null && record.createdAt.isBefore(now.minusHours(23))) throw ConflictException("Billing request needs manual reconciliation before retrying; do not submit a new key")
    }
}
