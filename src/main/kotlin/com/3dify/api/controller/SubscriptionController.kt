package com.omni3d.api.controller

import com.omni3d.api.model.dto.CreateCheckoutRequest
import com.omni3d.api.model.dto.PortalRequest
import com.omni3d.api.service.SubscriptionService
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.Authentication
import java.util.UUID

@RestController
@RequestMapping("/dashboard/subscription")
class SubscriptionController(
    private val subscriptionService: SubscriptionService
) {

    @GetMapping
    fun getStatus(authentication: Authentication) =
        subscriptionService.getStatus(UUID.fromString(authentication.principal as String))

    @PostMapping("/checkout")
    fun createCheckout(
        authentication: Authentication,
        @RequestBody request: CreateCheckoutRequest
    ) = subscriptionService.createCheckoutSession(
        userId = UUID.fromString(authentication.principal as String),
        planId = request.planId,
        successUrl = request.successUrl,
        cancelUrl = request.cancelUrl
    )

    @PostMapping("/portal")
    fun createPortal(
        authentication: Authentication,
        @RequestBody request: PortalRequest
    ) = subscriptionService.createBillingPortal(
        userId = UUID.fromString(authentication.principal as String),
        returnUrl = request.returnUrl
    )
}
