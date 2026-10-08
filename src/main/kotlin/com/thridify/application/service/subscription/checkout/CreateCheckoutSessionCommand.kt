package com.thridify.application.service.subscription.checkout

import java.util.UUID

data class CreateCheckoutSessionCommand(val userId: UUID, val planId: UUID, val successUrl: String, val cancelUrl: String, val requestId: UUID = UUID.randomUUID())
