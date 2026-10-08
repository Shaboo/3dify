package com.thridify.infrastructure.billing

import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import com.thridify.domain.billing.BillingEvent
import com.thridify.shared.exception.BadRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StripeBillingClientTest {
    private val secret = "whsec_test"
    private val client = StripeBillingClient(secret)
    private fun signature(payload: String): String {
        val time = Instant.now().epochSecond
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal("$time.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
        return "t=$time,v1=$hash"
    }

    @Test
    fun `verified checkout payload maps metadata and subscription without leaking stripe types`() {
        val user = UUID.randomUUID()
        val plan = UUID.randomUUID()
        val payload = """{"id":"evt_1","object":"event","api_version":"${Stripe.API_VERSION}","type":"checkout.session.completed","data":{"object":{"id":"cs_1","object":"checkout.session","metadata":{"userId":"$user","planId":"$plan"},"subscription":"sub_1"}}}"""
        assertEquals(BillingEvent.CheckoutCompleted(user, plan, "sub_1"), client.verifyEvent(payload, signature(payload)).event)
    }

    @Test
    fun `missing checkout metadata is ignored while invalid signatures keep the existing error`() {
        val payload = """{"id":"evt_1","object":"event","api_version":"${Stripe.API_VERSION}","type":"checkout.session.completed","data":{"object":{"id":"cs_1","object":"checkout.session","metadata":{},"subscription":"sub_1"}}}"""
        assertIs<BillingEvent.Ignored>(client.verifyEvent(payload, signature(payload)).event)
        assertEquals("Invalid Stripe signature", assertThrows<BadRequestException> { client.verifyEvent(payload, "invalid") }.message)
    }

    @Test
    fun `checkout preserves stripe mode line item redirect suffix and metadata`() {
        val user = UUID.randomUUID()
        val plan = UUID.randomUUID()
        mockkStatic(Session::class)
        try {
            val params = slot<SessionCreateParams>()
            every { Session.create(capture(params)) } returns mockk { every { url } returns "http://checkout" }
            assertEquals("http://checkout", client.createCheckout(user, plan, "price_1", "http://success", "http://cancel"))
            assertEquals(SessionCreateParams.Mode.SUBSCRIPTION, params.captured.mode)
            assertEquals("price_1", params.captured.lineItems.single().price)
            assertEquals(1L, params.captured.lineItems.single().quantity)
            assertEquals("http://success?session_id={CHECKOUT_SESSION_ID}", params.captured.successUrl)
            assertEquals("http://cancel", params.captured.cancelUrl)
            assertEquals(mapOf("userId" to user.toString(), "planId" to plan.toString()), params.captured.metadata)
        } finally {
            unmockkStatic(Session::class)
        }
    }
}
