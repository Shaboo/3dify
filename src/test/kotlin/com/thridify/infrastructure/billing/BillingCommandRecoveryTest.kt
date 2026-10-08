package com.thridify.infrastructure.billing
import com.thridify.IntegrationTestBase
import com.thridify.application.service.plan.createplan.CreatePlanApplicationService
import com.thridify.application.service.plan.createplan.CreatePlanCommand
import com.thridify.application.service.subscription.checkout.CreateCheckoutSessionApplicationService
import com.thridify.application.service.subscription.checkout.CreateCheckoutSessionCommand
import com.thridify.domain.billing.BillingClient
import com.thridify.domain.billing.BillingCommandPolicy
import com.thridify.domain.billing.BillingCommandRepository
import com.thridify.domain.plan.PlanPolicy
import com.thridify.domain.plan.PlanRepository
import com.thridify.domain.subscription.SubscriptionPolicy
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.exception.ConflictException
import com.thridify.shared.metrics.AppMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
class BillingCommandRecoveryTest : IntegrationTestBase() {
    @Autowired lateinit var commands: BillingCommandRepository

    @Autowired lateinit var plans: PlanRepository

    @Autowired lateinit var transactions: TransactionProvider

    @Autowired lateinit var metrics: AppMetrics
    private val policy = BillingCommandPolicy()

    @Test fun `remote price survives failed local insert and repeated plan command creates one plan`() {
        resetDatabase()
        val billing: BillingClient = mockk()
        val failingPlans: PlanRepository = mockk()
        every { failingPlans.findByName(any()) } answers { plans.findByName(firstArg()) }
        every { failingPlans.findById(any()) } answers { plans.findById(firstArg()) }
        var fail = true
        every { failingPlans.insert(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            if (fail) {
                fail = false
                error("local write failed")
            }
            plans.insert(arg(0), arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7), arg(8))
        }
        val request = CreatePlanCommand("paid", "Paid", null, 60, 50, 2900, "usd", 1)
        every { billing.createPrice("Paid", 2900, "usd", "plan:${request.requestId}") } answers {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            "price_saved"
        }
        val service = CreatePlanApplicationService(failingPlans, billing, PlanPolicy(), commands, policy, transactions)
        assertThrows<IllegalStateException> { service.execute(request) }
        assertEquals("price_saved", dsl.fetchOne("SELECT provider_result FROM billing_commands")?.get("provider_result"))
        val result = service.execute(request)
        assertEquals(result, service.execute(request))
        assertEquals(1, dsl.fetchCount(org.jooq.impl.DSL.table("plans")))
        verify(exactly = 1) { billing.createPrice(any(), any(), any(), any()) }
        assertThrows<ConflictException> { service.execute(request.copy(priceCents = 3900)) }
    }

    @Test fun `lost checkout result is recovered with the same provider key and then replayed locally`() {
        resetDatabase()
        val plan = plans.insert("paid", "Paid", null, 60, 50, 2900, "usd", "price_1", 1)
        val billing: BillingClient = mockk()
        val failingCommands: BillingCommandRepository = mockk()
        every { failingCommands.reserve(any(), any()) } answers { commands.reserve(firstArg(), secondArg()) }
        every { failingCommands.complete(any(), any()) } answers { commands.complete(firstArg(), secondArg()) }
        var fail = true
        every { failingCommands.recordProviderResult(any(), any()) } answers {
            if (fail) {
                fail = false
                error("database unavailable")
            }
            commands.recordProviderResult(firstArg(), secondArg())
        }
        val request = CreateCheckoutSessionCommand(UUID.randomUUID(), plan, "https://success.example", "https://cancel.example")
        val key = "checkout:${request.userId}:${request.requestId}"
        every { billing.createCheckout(request.userId, plan, "price_1", request.successUrl, request.cancelUrl, key) } answers {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            "https://stripe.example/original-session"
        }
        val service = CreateCheckoutSessionApplicationService(mockk(), plans, mockk(), metrics, billing, SubscriptionPolicy(), transactions, failingCommands, policy)
        assertThrows<IllegalStateException> { service.execute(request) }
        val result = service.execute(request)
        assertEquals(result, service.execute(request))
        verify(exactly = 2) { billing.createCheckout(any(), any(), any(), any(), any(), key) }
        assertThrows<ConflictException> { service.execute(request.copy(cancelUrl = "https://different.example")) }
        dsl.execute("UPDATE billing_commands SET result = NULL, provider_result = NULL, created_at = now() - interval '24 hours'")
        assertThrows<ConflictException> { service.execute(request) }
        verify(exactly = 2) { billing.createCheckout(any(), any(), any(), any(), any(), any()) }
    }
}
