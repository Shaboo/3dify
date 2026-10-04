package com.thridify.interfaces.webhook.shopify

import com.thridify.application.service.shopify.webhook.HandleShopifyWebhookApplicationService
import com.thridify.application.service.shopify.webhook.HandleShopifyWebhookCommand
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ShopifyWebhookController(private val handle: HandleShopifyWebhookApplicationService) {
    @PostMapping("/shopify/webhooks")
    @ResponseStatus(HttpStatus.OK)
    fun handle(@RequestBody body: ByteArray, @RequestHeader(value = "X-Shopify-Hmac-Sha256", defaultValue = "") signature: String, @RequestHeader("X-Shopify-Topic") topic: String, @RequestHeader("X-Shopify-Event-Id") eventId: String, @RequestHeader("X-Shopify-Shop-Domain") shopDomain: String, @RequestHeader(value = "X-Shopify-Triggered-At", required = false) triggeredAt: String?) = handle.execute(HandleShopifyWebhookCommand(body, signature, topic, eventId, shopDomain, triggeredAt))
}
