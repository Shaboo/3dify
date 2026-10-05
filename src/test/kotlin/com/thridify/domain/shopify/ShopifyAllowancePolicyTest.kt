package com.thridify.domain.shopify

import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class ShopifyAllowancePolicyTest {
    private val policy = ShopifyAllowancePolicy()
    private val start = OffsetDateTime.parse("2028-01-31T12:00:00Z")
    private val end = start.plusYears(1)

    @Test
    fun `annual months preserve original anchor through leap February and exact boundaries`() {
        val february = policy.period(start, end, "yearly", OffsetDateTime.parse("2028-02-29T12:00:00Z"))
        assertEquals(start.plusMonths(1), february.start)
        assertEquals(start.plusMonths(2), february.end)
        val march = policy.period(start, end, "yearly", OffsetDateTime.parse("2028-03-31T12:00:00Z"))
        assertEquals(start.plusMonths(2), march.start)
        assertEquals(start.plusMonths(3), march.end)
    }

    @Test
    fun `monthly cycles and expired annual cycles do not mint extra allowances`() {
        assertEquals(ShopifyAllowancePeriod(start, start.plusDays(30)), policy.period(start, start.plusDays(30), "monthly", start.plusDays(20)))
        assertEquals(ShopifyAllowancePeriod(start.plusMonths(11), end), policy.period(start, end, "yearly", end.plusMonths(1)))
    }
}
