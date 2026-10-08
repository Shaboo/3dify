package com.thridify.domain.generation

import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class DirectGenerationAllowancePolicy {
    fun allowance(account: DirectGenerationAccount?, now: OffsetDateTime): DirectGenerationAllowance {
        if (account == null || account.status !in setOf("active", "trialing")) throw ApiException(403, "No active subscription")
        if (account.provider == "internal") {
            val start = now.withOffsetSameInstant(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)
            return DirectGenerationAllowance(start, start.plusMonths(1), account.quota)
        }
        val end = account.periodEnd ?: throw ApiException(403, "Subscription has no confirmed billing period")
        val start = account.periodStart ?: end.minusMonths(1)
        if (now.isBefore(start) || !end.isAfter(now)) throw ApiException(403, "Subscription billing period has expired")
        return DirectGenerationAllowance(start, end, account.quota)
    }
    fun ensureConsumed(consumed: Boolean) {
        if (!consumed) throw ApiException(429, "This subscription has used its generation allowance")
    }
}
