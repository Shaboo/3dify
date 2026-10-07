package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyBillingSnapshot
import com.thridify.shared.exception.ApiException
import java.time.OffsetDateTime

open class ShopifyPartnerBillingClient(private val config: ShopifyProperties, private val http: ShopifyHttpClient) : ShopifyBillingClient {
    private var nextRequestNanos = 0L

    @Synchronized
    override fun currentSubscription(shopId: String): ShopifyBillingSnapshot? {
        config.requireEnabled()
        if (!Regex("[0-9]+").matches(config.partnerOrgId) || config.partnerAccessToken.isBlank() || !Regex("gid://shopify/App/[0-9]+").matches(config.appId)) throw ApiException(503, "Shopify billing is not configured")
        val now = OffsetDateTime.now(java.time.ZoneOffset.UTC)
        var windowEnd = now
        var windowStart = now.minusDays(365)
        val query = """
            query Billing(${ '$' }appId: ID!, ${ '$' }shopId: ID!, ${ '$' }from: DateTime!, ${ '$' }to: DateTime!) {
                activeSubscription(appId: ${ '$' }appId, shopId: ${ '$' }shopId) {
                    shop { id }
                    billingPeriod trialEndsAt legacySubscriptionId
                    currentBillingCycle { startTime endTime }
                    items { handle }
                }
                events(first: 1, filter: {subjectId: ${ '$' }appId, shopId: ${ '$' }shopId,
                    occurredAtMin: ${ '$' }from, occurredAtMax: ${ '$' }to,
                    eventTypes: [SUBSCRIPTION_CREATED, SUBSCRIPTION_CANCELED, SUBSCRIPTION_FROZEN, SUBSCRIPTION_UNFROZEN]}) {
                    edges { node { eventType } }
                }
            }
        """.trimIndent()
        val data = request(query, shopId, windowStart, windowEnd).path("data")
        if (!data.has("activeSubscription")) throw ApiException(502, "Shopify billing state is missing")
        val contract = data.path("activeSubscription")
        if (contract.isNull) return null
        if (contract.path("shop").path("id").asText() != shopId) throw ApiException(502, "Shopify billing identity mismatch")
        val cycle = contract.path("currentBillingCycle")
        val trialEnd = contract.path("trialEndsAt").takeUnless { it.isNull || it.isMissingNode }?.asText()
        var event = latestEvent(data)
        // The historical API limits a window to 365 days and otherwise defaults to 30 days.
        // Search backwards to Shopify's founding so a long-standing freeze cannot disappear.
        val historyStart = OffsetDateTime.parse("2006-01-01T00:00:00Z")
        while (event == null && windowStart.isAfter(historyStart)) {
            windowEnd = windowStart
            windowStart = windowEnd.minusDays(365).let { if (it.isBefore(historyStart)) historyStart else it }
            event = latestEvent(request(HISTORY_QUERY, shopId, windowStart, windowEnd).path("data"))
        }
        val status = when {
            event == "SUBSCRIPTION_FROZEN" -> "frozen"
            event == "SUBSCRIPTION_CANCELED" -> "canceled"
            trialEnd != null && parseDate(trialEnd).isAfter(now) -> "trialing"
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
            cycle.path("startTime").takeUnless { it.isMissingNode || it.isNull }?.asText()?.let(::parseDate),
            parseDate(end),
            contract.path("legacySubscriptionId").takeUnless { it.isNull || it.isMissingNode }?.asText(),
        )
    }
    private fun request(query: String, shopId: String, from: OffsetDateTime, to: OffsetDateTime): com.fasterxml.jackson.databind.JsonNode {
        val delay = nextRequestNanos - System.nanoTime()
        if (delay > 0) Thread.sleep(delay / 1_000_000, (delay % 1_000_000).toInt())
        nextRequestNanos = System.nanoTime() + config.partnerRequestIntervalMs.coerceAtLeast(0) * 1_000_000
        return http.post(
            "https://partners.shopify.com/${config.partnerOrgId}/api/${config.apiVersion}/graphql.json",
            mapOf("Content-Type" to "application/json", "X-Shopify-Access-Token" to config.partnerAccessToken),
            mapOf("query" to query, "variables" to mapOf("appId" to config.appId, "shopId" to shopId, "from" to from.toString(), "to" to to.toString())),
        )
    }

    private fun latestEvent(data: com.fasterxml.jackson.databind.JsonNode): String? {
        val edges = data.path("events").path("edges")
        if (!edges.isArray) throw ApiException(502, "Shopify billing lifecycle state is missing")
        if (edges.isEmpty) return null
        val event = edges.first().path("node").path("eventType").asText()
        if (event !in setOf("SUBSCRIPTION_CREATED", "SUBSCRIPTION_CANCELED", "SUBSCRIPTION_FROZEN", "SUBSCRIPTION_UNFROZEN")) throw ApiException(502, "Invalid Shopify billing lifecycle state")
        return event
    }

    private fun parseDate(value: String): OffsetDateTime = try {
        OffsetDateTime.parse(value)
    } catch (_: java.time.format.DateTimeParseException) {
        throw ApiException(502, "Invalid Shopify billing date")
    }

    private companion object {
        val HISTORY_QUERY = """
            query History(${ '$' }appId: ID!, ${ '$' }shopId: ID!, ${ '$' }from: DateTime!, ${ '$' }to: DateTime!) {
                events(first: 1, orderBy: OCCURRED_AT_DESC, filter: {subjectId: ${ '$' }appId, shopId: ${ '$' }shopId,
                    occurredAtMin: ${ '$' }from, occurredAtMax: ${ '$' }to,
                    eventTypes: [SUBSCRIPTION_CREATED, SUBSCRIPTION_CANCELED, SUBSCRIPTION_FROZEN, SUBSCRIPTION_UNFROZEN]}) {
                    edges { node { eventType } }
                }
            }
        """.trimIndent()
    }

    override fun pricingUrl(shopDomain: String): String {
        if (!Regex("[a-z0-9][a-z0-9-]*[.]myshopify[.]com").matches(shopDomain) || !Regex("[a-z0-9][a-z0-9-]*").matches(config.appHandle)) throw ApiException(503, "Shopify pricing is not configured")
        return "https://admin.shopify.com/store/${shopDomain.removeSuffix(".myshopify.com")}/charges/${config.appHandle}/pricing_plans"
    }
}
