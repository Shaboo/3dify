package com.thridify.application.service.shopify.reconcile

import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ReconcileShopifySubscriptionsApplicationService(private val stores: ShopifyStoreRepository, private val billing: ShopifyBillingClient, private val transactions: TransactionProvider, private val metrics: AppMetrics) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (store in stores.dueForReconciliation(20)) {
            try {
                val observedAt = OffsetDateTime.now()
                val snapshot = billing.currentSubscription(store.shopId)
                transactions.transaction { stores.synchronize(store, snapshot, observedAt) }
                metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_RECONCILIATION, AppMetrics.WorkflowOutcome.COMPLETED)
            } catch (ex: Exception) {
                metrics.recordWorkflow(AppMetrics.Workflow.SHOPIFY_RECONCILIATION, AppMetrics.WorkflowOutcome.FAILED)
                log.warn("Shopify subscription reconciliation failed for connection {} error_type={}", store.connectionId, ex.javaClass.simpleName)
            }
        }
    }
}
