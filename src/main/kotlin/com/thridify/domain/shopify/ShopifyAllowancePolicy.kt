package com.thridify.domain.shopify

import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Component
class ShopifyAllowancePolicy {
    fun period(start: OffsetDateTime, end: OffsetDateTime, interval: String, now: OffsetDateTime): ShopifyAllowancePeriod {
        require(end.isAfter(start)) { "Allowance period must end after its start" }
        if (interval != "yearly" || now.isBefore(start)) return ShopifyAllowancePeriod(start, end)
        var month = ChronoUnit.MONTHS.between(start.toLocalDate(), now.toLocalDate()).coerceAtLeast(0)
        while (start.plusMonths(month).isAfter(now) && month > 0) month--
        while (!start.plusMonths(month + 1).isAfter(now)) month++
        val from = start.plusMonths(month)
        val until = start.plusMonths(month + 1).let { if (it.isAfter(end)) end else it }
        // Expired subscriptions retain their last allowance window without minting a new one.
        if (!from.isBefore(end)) return period(start, end, interval, end.minusNanos(1))
        return ShopifyAllowancePeriod(from, until)
    }
}

data class ShopifyAllowancePeriod(val start: OffsetDateTime, val end: OffsetDateTime)
