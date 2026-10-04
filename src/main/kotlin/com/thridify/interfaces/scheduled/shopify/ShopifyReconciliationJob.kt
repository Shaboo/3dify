package com.thridify.interfaces.scheduled.shopify

import com.thridify.application.service.shopify.reconcile.ReconcileShopifySubscriptionsApplicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["shopify.enabled", "shopify.jobs-enabled"], havingValue = "true")
class ShopifyReconciliationJob(private val reconcile: ReconcileShopifySubscriptionsApplicationService) {
    @Scheduled(fixedDelayString = "\${shopify.reconciliation-delay-ms:60000}")
    fun run() = reconcile.execute()
}
