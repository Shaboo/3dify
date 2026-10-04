package com.thridify.interfaces.scheduled.shopify

import com.thridify.application.service.shopify.redact.RedactShopifyDataApplicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["shopify.enabled", "shopify.jobs-enabled"], havingValue = "true")
class ShopifyPrivacyJob(private val redact: RedactShopifyDataApplicationService) {
    @Scheduled(fixedDelayString = "\${shopify.privacy-delay-ms:60000}")
    fun run() = redact.execute()
}
