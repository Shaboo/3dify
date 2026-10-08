package com.thridify.domain.generation

import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectGenerationAllowancePolicyTest {
    @Test
    fun `free allowance resets by UTC month and paid allowances require an unexpired provider period`() {
        val policy = DirectGenerationAllowancePolicy()
        val now = OffsetDateTime.parse("2026-03-01T00:30:00+02:00")
        val account = DirectGenerationAccount(UUID.randomUUID(), "internal", "active", 100, null, null)
        assertEquals(OffsetDateTime.parse("2026-02-01T00:00:00Z"), policy.allowance(account, now).start)
        assertEquals(OffsetDateTime.parse("2026-03-01T00:00:00Z"), policy.allowance(account, now).end)
        assertFailsWith<com.thridify.shared.exception.ApiException> { policy.allowance(account.copy(provider = "stripe", periodEnd = now.minusSeconds(1)), now) }
    }
}
