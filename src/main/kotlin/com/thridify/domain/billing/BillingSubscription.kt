package com.thridify.domain.billing

import java.time.OffsetDateTime

data class BillingSubscription(val id: String, val customerId: String?, val status: String, val periodEnd: OffsetDateTime?)
