package com.thridify.infrastructure.billing
import com.thridify.IntegrationTestBase
import com.thridify.domain.billing.BillingEvent
import com.thridify.domain.billing.StripeEventRepository
import com.thridify.domain.billing.VerifiedBillingEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
class StripeEventReceiptTest : IntegrationTestBase() {
    @Autowired lateinit var receipts: StripeEventRepository

    @Test fun `duplicate delivery persists once and old receipt preserves latest event time`() {
        resetDatabase()
        val user = UUID.randomUUID()
        dsl.execute("INSERT INTO users(id, name, email, password_hash) VALUES (?, 'Test', ?, 'hash')", user, "$user@example.com")
        createDirectWorkspace(user)
        val now = OffsetDateTime.now().withNano(0)
        val current = VerifiedBillingEvent("evt-current", now, BillingEvent.Ignored)
        assertTrue(receipts.record(user, current))
        assertFalse(receipts.record(user, current))
        assertTrue(receipts.record(user, current.copy(id = "evt-old", createdAt = now.minusDays(1))))
        assertEquals(now.toInstant(), receipts.latest(user)?.toInstant())
    }
}
