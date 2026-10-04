package com.thridify.application.service.subscription.portal

import com.thridify.application.service.subscription.portal.PortalResult
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.subscription.SubscriptionPolicy
import com.thridify.domain.subscription.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class CreateBillingPortalApplicationService(private val subscriptions: SubscriptionRepository, private val billing: BillingClient, private val policy: SubscriptionPolicy) {
    fun execute(command: CreateBillingPortalCommand): PortalResult {
        val customer = policy.portalCustomer(subscriptions.findActiveByUserId(command.userId))
        return PortalResult(billing.createPortal(customer, command.returnUrl))
    }
}
