package com.thridify.domain.subscription

import com.thridify.domain.subscription.SubscriptionEntity
import com.thridify.domain.subscription.SubscriptionWithPlanEntity
import java.time.OffsetDateTime
import java.util.UUID

interface SubscriptionRepository {
    fun findActiveByUserId(userId: UUID): SubscriptionWithPlanEntity?
    fun findByStripeSubId(stripeSubId: String): SubscriptionEntity?
    fun findByStripeCustomerId(stripeCustomerId: String): SubscriptionEntity?
    fun insert(
        userId: UUID,
        planId: UUID,
        stripeSubId: String?,
        stripeCustomerId: String?,
        status: String,
        currentPeriodEnd: OffsetDateTime?,
    ): Unit
    fun upsertByUserId(
        userId: UUID,
        planId: UUID,
        stripeSubId: String?,
        stripeCustomerId: String?,
        status: String,
        currentPeriodEnd: OffsetDateTime?,
    ): Unit
    fun updateStatusByStripeSubId(stripeSubId: String, status: String, currentPeriodEnd: OffsetDateTime?): Unit
    fun updateStatusByStripeCustomerId(stripeCustomerId: String, status: String): Unit
}
