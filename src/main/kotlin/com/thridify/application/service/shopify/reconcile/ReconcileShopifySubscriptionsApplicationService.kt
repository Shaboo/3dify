package com.thridify.application.service.shopify.reconcile

import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.domain.transaction.TransactionProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ReconcileShopifySubscriptionsApplicationService(private val stores: ShopifyStoreRepository, private val billing: ShopifyBillingClient, private val transactions: TransactionProvider) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (store in stores.dueForReconciliation(20)) {
            try {
                val observedAt = OffsetDateTime.now()
                val snapshot = billing.currentSubscription(store.shopId)
                transactions.transaction { stores.synchronize(store, snapshot, observedAt) }
            } catch (ex: Exception) {
                log.warn("Shopify subscription reconciliation failed for connection {}", store.connectionId, ex)
            }
        }
    }
}
