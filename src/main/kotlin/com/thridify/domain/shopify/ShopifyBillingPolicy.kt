package com.thridify.domain.shopify
import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID
enum class ShopifyBillingObservation { DISCONNECTED, PENDING, REUSE, APPLY }

@Component
class ShopifyBillingPolicy {
    fun observation(status: String?, installed: OffsetDateTime?, checked: OffsetDateTime?, observed: OffsetDateTime): ShopifyBillingObservation = when {
        status != "connected" -> ShopifyBillingObservation.DISCONNECTED
        installed != null && observed.isBefore(installed) -> ShopifyBillingObservation.PENDING
        checked != null && !observed.isAfter(checked) -> ShopifyBillingObservation.REUSE
        else -> ShopifyBillingObservation.APPLY
    }
    fun selectPlan(candidates: List<UUID>): UUID? = candidates.distinct().singleOrNull()
    fun periodStart(snapshot: ShopifyBillingSnapshot, previousStart: OffsetDateTime?, previousEnd: OffsetDateTime?, observed: OffsetDateTime): OffsetDateTime {
        val start = snapshot.periodStart ?: previousStart?.takeIf { previousEnd?.isEqual(snapshot.periodEnd) == true } ?: observed
        if (!snapshot.periodEnd.isAfter(start)) throw ApiException(502, "Shopify returned an invalid billing period")
        return start
    }
    fun generationLimit(snapshot: ShopifyBillingSnapshot, planQuota: Int): Int = snapshot.localTestGenerationLimit ?: planQuota
}
