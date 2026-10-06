package com.thridify.interfaces.rest.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith("/actuator/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val previous = MDC.getCopyOfContextMap()
        val requestId = request.getHeader("X-Request-ID")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } ?: UUID.randomUUID().toString()
        val started = System.nanoTime()
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-ID", requestId)
        var failed = false
        try {
            filterChain.doFilter(request, response)
        } catch (ex: Throwable) {
            failed = true
            throw ex
        } finally {
            // Never log raw paths: webhook paths can contain secrets, and query strings contain user data.
            val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString() ?: "unmatched"
            log.info("http_request method={} route={} status={} duration_ms={}", request.method, route, if (failed) 500 else response.status, (System.nanoTime() - started) / 1_000_000)
            if (previous == null) MDC.clear() else MDC.setContextMap(previous)
        }
    }
}
