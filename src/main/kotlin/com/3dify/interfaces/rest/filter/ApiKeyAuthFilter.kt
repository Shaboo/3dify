package com.`3dify`.interfaces.rest.filter

import com.`3dify`.application.service.access.authorize.ApiAuthorizationResult
import com.`3dify`.application.service.access.authorize.AuthorizeApiRequestApplicationService
import com.`3dify`.application.service.access.authorize.AuthorizeApiRequestCommand
import com.`3dify`.shared.metrics.AppMetrics
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthFilter(private val authorize: AuthorizeApiRequestApplicationService, private val metrics: AppMetrics) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.startsWith("/api/v1/")
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val rawKey = request.getHeader("X-API-KEY")
        if (rawKey.isNullOrBlank()) {
            metrics.authFailures.increment()
            response.sendError(401, "Missing X-API-KEY header")
            return
        }
        when (val result = authorize.execute(AuthorizeApiRequestCommand(rawKey))) {
            is ApiAuthorizationResult.Denied -> response.sendError(result.statusCode, result.message)

            is ApiAuthorizationResult.Authorized -> {
                MDC.put("apiKeyId", result.keyId.toString())
                MDC.put("userId", result.userId.toString())
                try {
                    SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(result.keyId.toString(), rawKey, listOf(SimpleGrantedAuthority("ROLE_API_USER")))
                    filterChain.doFilter(request, response)
                } finally {
                    MDC.remove("apiKeyId")
                    MDC.remove("userId")
                }
            }
        }
    }
}
