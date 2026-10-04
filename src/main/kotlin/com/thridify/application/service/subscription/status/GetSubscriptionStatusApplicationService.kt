package com.thridify.application.service.subscription.status

import com.thridify.application.service.subscription.status.SubscriptionStatusResult
import com.thridify.domain.subscription.SubscriptionPolicy
import com.thridify.domain.subscription.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class GetSubscriptionStatusApplicationService(private val subscriptions: SubscriptionRepository, private val policy: SubscriptionPolicy) {
    fun execute(query: GetSubscriptionStatusQuery): SubscriptionStatusResult {
        val sub = subscriptions.findActiveByUserId(query.userId) ?: return SubscriptionStatusResult(null, null, null, null, null, null, false)
        return SubscriptionStatusResult(
            sub.planId,
            sub.planName,
            sub.planDisplayName,
            sub.planPriceCents,
            sub.status,
            sub.currentPeriodEnd?.toString(),
            policy.isActive(sub.status),
        )
    }
}
