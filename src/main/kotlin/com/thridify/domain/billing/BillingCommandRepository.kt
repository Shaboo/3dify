package com.thridify.domain.billing
import java.time.OffsetDateTime
data class BillingCommandRecord(val key: String, val fingerprint: String, val createdAt: OffsetDateTime, val providerResult: String?, val result: String?)
interface BillingCommandRepository {
    fun reserve(key: String, fingerprint: String): BillingCommandRecord
    fun lock(key: String): BillingCommandRecord
    fun recordProviderResult(key: String, value: String)
    fun complete(key: String, result: String)
}
