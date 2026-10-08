package com.thridify.domain.billing
import java.time.OffsetDateTime
import java.util.UUID
interface StripeEventRepository {
    fun latest(userId: UUID): OffsetDateTime?
    fun record(userId: UUID, event: VerifiedBillingEvent): Boolean
}
