package com.`3dify`.application.service.subscription.portal

import com.`3dify`.application.service.subscription.portal.PortalResult
import com.`3dify`.domain.billing.BillingClient
import com.`3dify`.domain.subscription.SubscriptionPolicy
import com.`3dify`.domain.subscription.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class CreateBillingPortalApplicationService(private val subscriptions: SubscriptionRepository, private val billing: BillingClient, private val policy: SubscriptionPolicy) {
    fun execute(command: CreateBillingPortalCommand): PortalResult {
        val customer = policy.portalCustomer(subscriptions.findActiveByUserId(command.userId))
        return PortalResult(billing.createPortal(customer, command.returnUrl))
    }
}
