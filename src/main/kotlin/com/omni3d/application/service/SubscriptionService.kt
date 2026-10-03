package com.omni3d.application.service

import com.omni3d.shared.exception.ApiException
import com.omni3d.shared.exception.NotFoundException
import com.omni3d.shared.metrics.AppMetrics
import com.omni3d.interfaces.rest.dto.CheckoutResponse
import com.omni3d.interfaces.rest.dto.PortalResponse
import com.omni3d.interfaces.rest.dto.SubscriptionStatusResponse
import com.omni3d.infrastructure.persistence.ApiKeyRepository
import com.omni3d.infrastructure.persistence.PlanRepository
import com.omni3d.infrastructure.persistence.SubscriptionRepository
import com.stripe.model.Customer
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerListParams
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val planRepository: PlanRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val metrics: AppMetrics,
    @Value("\${stripe.webhook-secret}") private val webhookSecret: String
) {
    private val log = LoggerFactory.getLogger(SubscriptionService::class.java)

    fun getStatus(userId: UUID): SubscriptionStatusResponse {
        val sub = subscriptionRepository.findActiveByUserId(userId)
            ?: return SubscriptionStatusResponse(null, null, null, null, null, null, false)

        return SubscriptionStatusResponse(
            planId          = sub.planId,
            planName        = sub.planName,
            displayName     = sub.planDisplayName,
            priceCents      = sub.planPriceCents,
            status          = sub.status,
            currentPeriodEnd= sub.currentPeriodEnd?.toString(),
            isActive        = sub.status in listOf("active", "trialing")
        )
    }

    @Transactional
    fun createCheckoutSession(userId: UUID, planId: UUID, successUrl: String, cancelUrl: String): CheckoutResponse {
        log.info("Creating checkout session [userId={}, planId={}]", userId, planId)

        val plan = planRepository.findById(planId)
            ?: run {
                log.warn("Checkout failed -- plan not found [planId={}]", planId)
                throw NotFoundException("Plan not found: $planId")
            }

        if (plan.priceCents == 0) {
            log.info("Free plan activation [userId={}, plan={}]", userId, plan.name)
            subscriptionRepository.upsertByUserId(
                userId = userId, planId = planId,
                stripeSubId = null, stripeCustomerId = null,
                status = "active", currentPeriodEnd = null
            )
            apiKeyRepository.updatePlanForUser(userId, planId)
            metrics.subscriptionsActivated.increment()
            return CheckoutResponse(checkoutUrl = successUrl)
        }

        if (plan.stripePriceId.isNullOrBlank()) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Plan is not linked to a Stripe price")
        }

        val session = Session.create(
            SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(plan.stripePriceId)
                        .setQuantity(1L)
                        .build()
                )
                .setSuccessUrl("$successUrl?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .putMetadata("userId", userId.toString())
                .putMetadata("planId", planId.toString())
                .build()
        )
        log.info("Stripe checkout session created [userId={}, sessionId={}]", userId, session.id)
        return CheckoutResponse(checkoutUrl = session.url)
    }

    fun createBillingPortal(userId: UUID, returnUrl: String): PortalResponse {
        val sub = subscriptionRepository.findActiveByUserId(userId)
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "No active subscription found")
        val customerId = sub.stripeCustomerId
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "No Stripe customer linked to this subscription")
        val session = com.stripe.model.billingportal.Session.create(
            PortalSessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build()
        )
        log.info("Billing portal session created [userId={}]", userId)
        return PortalResponse(portalUrl = session.url)
    }

    @Transactional
    fun handleStripeWebhook(payload: String, sigHeader: String) {
        val event: Event = try {
            Webhook.constructEvent(payload, sigHeader, webhookSecret)
        } catch (ex: Exception) {
            log.warn("Stripe webhook signature verification failed: {}", ex.message)
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid Stripe signature")
        }

        log.info("Stripe event received [type={}]", event.type)
        when (event.type) {
            "checkout.session.completed"        -> handleCheckoutCompleted(event)
            "customer.subscription.updated"     -> handleSubscriptionUpdated(event)
            "customer.subscription.deleted"     -> handleSubscriptionDeleted(event)
            "invoice.payment_failed"            -> handlePaymentFailed(event)
            else -> log.debug("Unhandled Stripe event [type={}]", event.type)
        }
    }

    private fun handleCheckoutCompleted(event: Event) {
        val session   = event.dataObjectDeserializer.`object`.orElse(null) as? Session ?: return
        val userId    = UUID.fromString(session.metadata["userId"] ?: return)
        val planId    = UUID.fromString(session.metadata["planId"] ?: return)
        val stripeSubId = session.subscription ?: return
        val stripeSub = Subscription.retrieve(stripeSubId)

        subscriptionRepository.upsertByUserId(
            userId           = userId,
            planId           = planId,
            stripeSubId      = stripeSubId,
            stripeCustomerId = stripeSub.customer,
            status           = stripeSub.status,
            currentPeriodEnd = epochToOffsetDateTime(stripeSub.currentPeriodEnd)
        )
        apiKeyRepository.updatePlanForUser(userId, planId)
        metrics.subscriptionsActivated.increment()
        log.info("Subscription activated via Stripe checkout [userId={}, planId={}]", userId, planId)
    }

    private fun handleSubscriptionUpdated(event: Event) {
        val sub = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription ?: return
        subscriptionRepository.updateStatusByStripeSubId(
            stripeSubId      = sub.id,
            status           = sub.status,
            currentPeriodEnd = epochToOffsetDateTime(sub.currentPeriodEnd)
        )
        log.info("Subscription updated [stripeSubId={}, status={}]", sub.id, sub.status)
        if (sub.status == "active") {
            val userId = subscriptionRepository.findByStripeSubId(sub.id)?.userId ?: return
            apiKeyRepository.setActiveByUserId(userId, true)
            metrics.subscriptionsActivated.increment()
        }
    }

    private fun handleSubscriptionDeleted(event: Event) {
        val sub    = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription ?: return
        subscriptionRepository.updateStatusByStripeSubId(sub.id, "canceled", null)
        val userId = subscriptionRepository.findByStripeSubId(sub.id)?.userId ?: return
        apiKeyRepository.setActiveByUserId(userId, false)
        metrics.subscriptionsCanceled.increment()
        log.info("Subscription canceled -- API keys deactivated [userId={}]", userId)
    }

    private fun handlePaymentFailed(event: Event) {
        val invoice    = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice ?: return
        val customerId = invoice.customer ?: return
        subscriptionRepository.updateStatusByStripeCustomerId(customerId, "past_due")
        metrics.subscriptionsPastDue.increment()
        log.warn("Payment failed -- subscription marked past_due [customerId={}]", customerId)
    }

    private fun epochToOffsetDateTime(epochSeconds: Long?): OffsetDateTime? =
        epochSeconds?.let { OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }
}
