package com.`3dify`.infrastructure.billing

import com.`3dify`.domain.billing.BillingClient
import com.`3dify`.domain.billing.BillingEvent
import com.`3dify`.domain.billing.BillingSubscription
import com.`3dify`.shared.exception.BadRequestException
import com.stripe.model.Invoice
import com.stripe.model.Price
import com.stripe.model.Product
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.PriceCreateParams
import com.stripe.param.ProductCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams

@Component
class StripeBillingClient(@Value("\${stripe.webhook-secret}") private val webhookSecret: String) : BillingClient {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun createPrice(displayName: String, priceCents: Int, currency: String): String {
        val product = Product.create(ProductCreateParams.builder().setName(displayName).build())
        return Price.create(
            PriceCreateParams.builder().setProduct(product.id).setUnitAmount(priceCents.toLong())
                .setCurrency(currency).setRecurring(
                    PriceCreateParams.Recurring.builder()
                        .setInterval(PriceCreateParams.Recurring.Interval.MONTH).build(),
                ).build(),
        ).id
    }
    override fun createCheckout(userId: UUID, planId: UUID, priceId: String, successUrl: String, cancelUrl: String): String = Session.create(
        SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
            .setSuccessUrl("$successUrl?session_id={CHECKOUT_SESSION_ID}").setCancelUrl(cancelUrl)
            .putMetadata("userId", userId.toString()).putMetadata("planId", planId.toString()).build(),
    ).url

    override fun createPortal(customerId: String, returnUrl: String): String = com.stripe.model.billingportal.Session.create(
        PortalSessionCreateParams.builder()
            .setCustomer(customerId).setReturnUrl(returnUrl).build(),
    ).url

    override fun retrieveSubscription(id: String) = Subscription.retrieve(id).toDomain()

    override fun verifyEvent(payload: String, signature: String): BillingEvent {
        val event = try {
            Webhook.constructEvent(payload, signature, webhookSecret)
        } catch (ex: Exception) {
            log.warn("Stripe webhook signature verification failed: {}", ex.message)
            throw BadRequestException("Invalid Stripe signature")
        }
        log.info("Stripe event received [type={}]", event.type)
        // Deserialize only handled types, matching the previous webhook behavior.
        val value by lazy { event.dataObjectDeserializer.`object`.orElse(null) }
        return when (event.type) {
            "checkout.session.completed" -> {
                val session = value as? Session ?: return BillingEvent.Ignored
                val userId = UUID.fromString(session.metadata["userId"] ?: return BillingEvent.Ignored)
                val planId = UUID.fromString(session.metadata["planId"] ?: return BillingEvent.Ignored)
                BillingEvent.CheckoutCompleted(userId, planId, session.subscription ?: return BillingEvent.Ignored)
            }

            "customer.subscription.updated" -> (value as? Subscription)?.let { BillingEvent.SubscriptionUpdated(it.toDomain()) } ?: BillingEvent.Ignored

            "customer.subscription.deleted" -> (value as? Subscription)?.let { BillingEvent.SubscriptionDeleted(it.id) } ?: BillingEvent.Ignored

            "invoice.payment_failed" -> (value as? Invoice)?.customer?.let { BillingEvent.PaymentFailed(it) } ?: BillingEvent.Ignored

            else -> BillingEvent.Ignored
        }
    }
    private fun Subscription.toDomain() = BillingSubscription(
        id,
        customer,
        status,
        currentPeriodEnd?.let { OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) },
    )
}
