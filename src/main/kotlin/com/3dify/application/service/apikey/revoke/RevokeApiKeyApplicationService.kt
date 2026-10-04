package com.`3dify`.application.service.apikey.revoke

import com.`3dify`.domain.access.ApiKeyPolicy
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.shared.metrics.AppMetrics
import org.springframework.stereotype.Service

@Service
class RevokeApiKeyApplicationService(private val keys: ApiKeyRepository, private val metrics: AppMetrics, private val policy: ApiKeyPolicy) {
    fun execute(command: RevokeApiKeyCommand) {
        policy.ensureRevoked(keys.revoke(command.userId, command.keyId))
        metrics.apiKeysRevoked.increment()
    }
}
