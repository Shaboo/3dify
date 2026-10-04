package com.thridify.domain.billing

import java.util.UUID

interface BillingClient {
    fun createPrice(displayName: String, priceCents: Int, currency: String): String
    fun createCheckout(userId: UUID, planId: UUID, priceId: String, successUrl: String, cancelUrl: String): String
    fun createPortal(customerId: String, returnUrl: String): String
    fun verifyEvent(payload: String, signature: String): BillingEvent
    fun retrieveSubscription(id: String): BillingSubscription
}
