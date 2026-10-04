package com.omni3d.interfaces.webhook

import com.omni3d.application.service.SubscriptionService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks/stripe")
class StripeWebhookController(
    private val subscriptionService: SubscriptionService
) {

    @PostMapping
    fun handleWebhook(request: HttpServletRequest): ResponseEntity<String> {
        val payload = request.inputStream.bufferedReader().readText()
        val sigHeader = request.getHeader("Stripe-Signature")
            ?: return ResponseEntity.badRequest().body("Missing Stripe-Signature header")

        subscriptionService.handleStripeWebhook(payload, sigHeader)
        return ResponseEntity.ok("received")
    }
}
