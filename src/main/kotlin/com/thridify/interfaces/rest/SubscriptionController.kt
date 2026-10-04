package com.thridify.interfaces.rest

import com.thridify.application.service.subscription.checkout.CreateCheckoutSessionApplicationService
import com.thridify.application.service.subscription.checkout.CreateCheckoutSessionCommand
import com.thridify.application.service.subscription.portal.CreateBillingPortalApplicationService
import com.thridify.application.service.subscription.portal.CreateBillingPortalCommand
import com.thridify.application.service.subscription.status.GetSubscriptionStatusApplicationService
import com.thridify.application.service.subscription.status.GetSubscriptionStatusQuery
import com.thridify.interfaces.rest.dto.CreateCheckoutRequest
import com.thridify.interfaces.rest.dto.PortalRequest
import com.thridify.interfaces.rest.dto.toResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/dashboard/subscription")
class SubscriptionController(
    private val status: GetSubscriptionStatusApplicationService,
    private val checkout: CreateCheckoutSessionApplicationService,
    private val portal: CreateBillingPortalApplicationService,
) {
    @GetMapping
    fun getStatus(authentication: Authentication) = status.execute(GetSubscriptionStatusQuery(UUID.fromString(authentication.principal as String))).toResponse()

    @PostMapping("/checkout")
    fun createCheckout(authentication: Authentication, @RequestBody request: CreateCheckoutRequest) = checkout.execute(CreateCheckoutSessionCommand(UUID.fromString(authentication.principal as String), request.planId, request.successUrl, request.cancelUrl)).toResponse()

    @PostMapping("/portal")
    fun createPortal(authentication: Authentication, @RequestBody request: PortalRequest) = portal.execute(CreateBillingPortalCommand(UUID.fromString(authentication.principal as String), request.returnUrl)).toResponse()
}
