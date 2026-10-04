package com.thridify.domain.billing

import java.util.UUID

sealed interface BillingEvent {
    data class CheckoutCompleted(val userId: UUID, val planId: UUID, val subscriptionId: String) : BillingEvent
    data class SubscriptionUpdated(val subscription: BillingSubscription) : BillingEvent
    data class SubscriptionDeleted(val subscriptionId: String) : BillingEvent
    data class PaymentFailed(val customerId: String) : BillingEvent
    data object Ignored : BillingEvent
}
