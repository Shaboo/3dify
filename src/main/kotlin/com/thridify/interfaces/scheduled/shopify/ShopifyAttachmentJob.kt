package com.thridify.interfaces.scheduled.shopify

import com.thridify.application.service.shopify.attach.AttachShopifyModelsApplicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["shopify.enabled", "shopify.jobs-enabled"], havingValue = "true")
class ShopifyAttachmentJob(private val attach: AttachShopifyModelsApplicationService) {
    @Scheduled(fixedDelayString = "\${shopify.attachment-delay-ms:15000}")
    fun run() = attach.execute()
}
