package com.omni3d.interfaces.rest.filter

import com.omni3d.shared.metrics.AppMetrics
import com.omni3d.infrastructure.persistence.SubscriptionRepository
import com.omni3d.application.service.ApiKeyService
import com.omni3d.application.service.RateLimiterService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService,
    private val rateLimiterService: RateLimiterService,
    private val subscriptionRepository: SubscriptionRepository,
    private val metrics: AppMetrics
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ApiKeyAuthFilter::class.java)

    companion object {
        private const val API_KEY_HEADER = "X-API-KEY"
    }

    // Only run this filter for /api/v1/** requests
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/v1/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rawKey = request.getHeader(API_KEY_HEADER)

        if (rawKey.isNullOrBlank()) {
            log.debug("API auth rejected -- missing X-API-KEY header [uri={}]", request.requestURI)
            metrics.authFailures.increment()
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing X-API-KEY header")
            return
        }

        val apiKey = apiKeyService.validateRawKey(rawKey)
        if (apiKey == null) {
            log.warn("API auth rejected -- invalid or revoked key [uri={}]", request.requestURI)
            metrics.authFailures.increment()
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or revoked API key")
            return
        }

        MDC.put("apiKeyId", apiKey.id.toString())
        MDC.put("userId", apiKey.userId.toString())

        try {
            // Subscription check
            val subscription = subscriptionRepository.findActiveByUserId(apiKey.userId)
            if (subscription == null) {
                log.warn("API auth rejected -- no active subscription [userId={}]", apiKey.userId)
                metrics.authFailuresSubscription.increment()
                response.sendError(HttpStatus.FORBIDDEN.value(), "No active subscription")
                return
            }
            val subStatus = subscription.status
            if (subStatus !in listOf("active", "trialing")) {
                val message = when (subStatus) {
                    "past_due" -> "Subscription payment overdue -- please update your billing details"
                    "canceled"  -> "Subscription canceled -- please re-subscribe at the dashboard"
                    else        -> "Subscription inactive"
                }
                log.warn("API auth rejected -- subscription {} [userId={}]", subStatus, apiKey.userId)
                metrics.authFailuresSubscription.increment()
                response.sendError(HttpStatus.FORBIDDEN.value(), message)
                return
            }

            // Rate limit check
            if (!rateLimiterService.isAllowed(apiKey.id, apiKey.rateLimitRpm)) {
                log.warn("API request rate-limited [apiKeyId={}, limit={}rpm]", apiKey.id, apiKey.rateLimitRpm)
                metrics.rateLimitRejections.increment()
                metrics.authFailuresRateLimit.increment()
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded")
                return
            }

            val auth = UsernamePasswordAuthenticationToken(
                apiKey.id.toString(),
                rawKey,
                listOf(SimpleGrantedAuthority("ROLE_API_USER"))
            )
            SecurityContextHolder.getContext().authentication = auth
            filterChain.doFilter(request, response)

        } finally {
            MDC.remove("apiKeyId")
            MDC.remove("userId")
        }
    }
}
