package com.thridify.application.service.apikey.revoke

import com.thridify.domain.access.ApiKeyPolicy
import com.thridify.domain.apikey.ApiKeyRepository
import com.thridify.shared.metrics.AppMetrics
import org.springframework.stereotype.Service

@Service
class RevokeApiKeyApplicationService(private val keys: ApiKeyRepository, private val metrics: AppMetrics, private val policy: ApiKeyPolicy) {
    fun execute(command: RevokeApiKeyCommand) {
        policy.ensureRevoked(keys.revoke(command.userId, command.keyId))
        metrics.apiKeysRevoked.increment()
    }
}
