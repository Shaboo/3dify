package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyBillingSnapshot
import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ShopifyPartnerBillingClient(private val config: ShopifyProperties, private val http: ShopifyHttpClient) : ShopifyBillingClient {
    private var nextRequestNanos = 0L

    @Synchronized
    override fun currentSubscription(shopId: String): ShopifyBillingSnapshot? {
        config.requireEnabled()
        if (!Regex("[0-9]+").matches(config.partnerOrgId) || config.partnerAccessToken.isBlank() || !Regex("gid://shopify/App/[0-9]+").matches(config.appId)) throw ApiException(503, "Shopify billing is not configured")
        val delay = nextRequestNanos - System.nanoTime()
        if (delay > 0) Thread.sleep(delay / 1_000_000, (delay % 1_000_000).toInt())
        nextRequestNanos = System.nanoTime() + 300_000_000
        val query = """
            query Billing(${ '$' }appId: ID!, ${ '$' }shopId: ID!) {
                activeSubscription(appId: ${ '$' }appId, shopId: ${ '$' }shopId) {
                    shop { id }
                    billingPeriod trialEndsAt legacySubscriptionId
                    currentBillingCycle { startTime endTime }
                    items { handle }
                }
                events(first: 1, filter: {subjectId: ${ '$' }appId, shopId: ${ '$' }shopId,
                    eventTypes: [SUBSCRIPTION_CREATED, SUBSCRIPTION_UPDATED, SUBSCRIPTION_CANCELLATION_SCHEDULED,
                                 SUBSCRIPTION_CANCELED, SUBSCRIPTION_FROZEN, SUBSCRIPTION_UNFROZEN]}) {
                    edges { node { eventType } }
                }
            }
        """.trimIndent()
        val data = http.post(
            "https://partners.shopify.com/${config.partnerOrgId}/api/${config.apiVersion}/graphql.json",
            mapOf("Content-Type" to "application/json", "X-Shopify-Access-Token" to config.partnerAccessToken),
            mapOf("query" to query, "variables" to mapOf("appId" to config.appId, "shopId" to shopId)),
        ).path("data")
        if (!data.has("activeSubscription")) throw ApiException(502, "Shopify billing state is missing")
        val contract = data.path("activeSubscription")
        if (contract.isNull) return null
        if (contract.path("shop").path("id").asText() != shopId) throw ApiException(502, "Shopify billing identity mismatch")
        val cycle = contract.path("currentBillingCycle")
        val trialEnd = contract.path("trialEndsAt").takeUnless { it.isNull || it.isMissingNode }?.asText()
        val status = when {
            data.path("events").path("edges").firstOrNull()?.path("node")?.path("eventType")?.asText() == "SUBSCRIPTION_FROZEN" -> "frozen"
            trialEnd != null -> "trialing"
            else -> "active"
        }
        val end = cycle.path("endTime").takeUnless { it.isNull || it.isMissingNode }?.asText() ?: trialEnd ?: throw ApiException(502, "Shopify billing period is missing")
        val interval = when (contract.path("billingPeriod").asText()) {
            "EVERY_30_DAYS" -> "monthly"
            "ANNUAL" -> "yearly"
            else -> throw ApiException(502, "Unsupported Shopify billing interval")
        }
        return ShopifyBillingSnapshot(
            contract.path("items").map { it.path("handle").asText() }.filter { it.isNotBlank() }.toSet(),
            interval,
            status,
            cycle.path("startTime").takeUnless { it.isMissingNode || it.isNull }?.asText()?.let(OffsetDateTime::parse),
            OffsetDateTime.parse(end),
            contract.path("legacySubscriptionId").takeUnless { it.isNull || it.isMissingNode }?.asText(),
        )
    }
    override fun pricingUrl(shopDomain: String): String {
        if (!Regex("[a-z0-9][a-z0-9-]*[.]myshopify[.]com").matches(shopDomain) || !Regex("[a-z0-9][a-z0-9-]*").matches(config.appHandle)) throw ApiException(503, "Shopify pricing is not configured")
        return "https://admin.shopify.com/store/${shopDomain.removeSuffix(".myshopify.com")}/charges/${config.appHandle}/pricing_plans"
    }
}
