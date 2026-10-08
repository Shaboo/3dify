package com.thridify.domain.shopify
import com.thridify.shared.exception.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
class ShopifyStatePoliciesTest {
    private val attachments = ShopifyAttachmentPolicy()
    private val billing = ShopifyBillingPolicy()

    @Test fun `uncertain upload is checked even at retry limit and never resubmitted`() {
        val result = attachments.failed("waiting", 120, true, IllegalStateException("timeout"))
        assertEquals("checking", result.status)
        assertEquals(false, attachments.mayUpload(result.status))
        assertEquals("failed", attachments.failed("checking", 120, false, IllegalStateException()).status)
        assertEquals("retrying", attachments.failed("waiting", 9, false, ShopifyAttachmentException(false, true, "busy")).status)
        assertEquals("failed", attachments.failed("waiting", 10, false, ShopifyAttachmentException(false, true, "busy")).status)
        assertEquals("attached", attachments.observed(ShopifyAttachedModel("model", "READY"), 120).status)
        assertEquals("failed", attachments.observed(null, 120).status)
    }

    @Test fun `offers must map unambiguously and existing billing period survives missing start`() {
        val one = UUID.randomUUID()
        assertEquals(null, billing.selectPlan(emptyList()))
        assertEquals(null, billing.selectPlan(listOf(one, UUID.randomUUID())))
        assertEquals(one, billing.selectPlan(listOf(one, one)))
        val now = OffsetDateTime.now()
        val snapshot = ShopifyBillingSnapshot(setOf("offer"), "monthly", "active", null, now.plusMonths(1), "sub")
        assertEquals(now, billing.periodStart(snapshot, now, snapshot.periodEnd, now.plusDays(5)))
        assertThrows<ApiException> { billing.periodStart(snapshot.copy(periodStart = snapshot.periodEnd), null, null, now) }
        assertEquals(8, billing.generationLimit(snapshot.copy(localTestGenerationLimit = 8), 50))
        assertEquals(ShopifyBillingObservation.REUSE, billing.observation("connected", now.minusDays(1), now, now))
        assertEquals(ShopifyBillingObservation.PENDING, billing.observation("connected", now.plusDays(1), null, now))
    }
}
