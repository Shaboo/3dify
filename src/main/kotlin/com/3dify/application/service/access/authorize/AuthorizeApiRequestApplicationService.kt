package com.`3dify`.application.service.access.authorize

import com.`3dify`.domain.access.ApiKeyPolicy
import com.`3dify`.domain.access.RequestRateLimiter
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.subscription.SubscriptionRepository
import com.`3dify`.shared.metrics.AppMetrics
import org.springframework.stereotype.Service
import java.util.UUID

sealed interface ApiAuthorizationResult {
    data class Authorized(val keyId: UUID, val userId: UUID) : ApiAuthorizationResult
    data class Denied(val statusCode: Int, val message: String) : ApiAuthorizationResult
}

@Service
class AuthorizeApiRequestApplicationService(
    private val keys: ApiKeyRepository,
    private val subscriptions: SubscriptionRepository,
    private val rateLimiter: RequestRateLimiter,
    private val policy: ApiKeyPolicy,
    private val metrics: AppMetrics,
) {
    fun execute(command: AuthorizeApiRequestCommand): ApiAuthorizationResult {
        val key = if (policy.hasValidPrefix(command.rawKey)) keys.findByKeyHash(policy.hash(command.rawKey)) else null
        if (!policy.isValid(key)) {
            metrics.apiKeyValidationsFailed.increment()
            metrics.authFailures.increment()
            return ApiAuthorizationResult.Denied(401, "Invalid or revoked API key")
        }
        metrics.apiKeyValidations.increment()
        key!!
        val denied = policy.subscriptionDenial(subscriptions.findActiveByUserId(key.userId))
        if (denied != null) {
            metrics.authFailuresSubscription.increment()
            return ApiAuthorizationResult.Denied(403, denied)
        }
        if (!rateLimiter.isAllowed(key.id, key.rateLimitRpm)) {
            metrics.rateLimitRejections.increment()
            metrics.authFailuresRateLimit.increment()
            return ApiAuthorizationResult.Denied(429, "Rate limit exceeded")
        }
        return ApiAuthorizationResult.Authorized(key.id, key.userId)
    }
}
