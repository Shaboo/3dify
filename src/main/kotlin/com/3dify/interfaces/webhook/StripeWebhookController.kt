package com.`3dify`.interfaces.webhook

import com.`3dify`.application.service.subscription.webhook.HandleStripeWebhookApplicationService
import com.`3dify`.application.service.subscription.webhook.HandleStripeWebhookCommand
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks/stripe")
class StripeWebhookController(
    private val handleStripeWebhook: HandleStripeWebhookApplicationService,
) {

    @PostMapping
    fun handleWebhook(request: HttpServletRequest): ResponseEntity<String> {
        val payload = request.inputStream.bufferedReader().readText()
        val sigHeader = request.getHeader("Stripe-Signature")
            ?: return ResponseEntity.badRequest().body("Missing Stripe-Signature header")

        handleStripeWebhook.execute(HandleStripeWebhookCommand(payload, sigHeader))
        return ResponseEntity.ok("received")
    }
}
