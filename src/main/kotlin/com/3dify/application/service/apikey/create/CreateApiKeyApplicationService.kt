package com.`3dify`.application.service.apikey.create

import com.`3dify`.application.service.apikey.create.ApiKeyCreatedResult
import com.`3dify`.domain.access.ApiKeyPolicy
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.plan.PlanRepository
import com.`3dify`.shared.metrics.AppMetrics
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CreateApiKeyApplicationService(
    private val keys: ApiKeyRepository,
    private val plans: PlanRepository,
    private val metrics: AppMetrics,
    private val policy: ApiKeyPolicy,
) {
    fun execute(command: CreateApiKeyCommand): ApiKeyCreatedResult {
        val plan = policy.requirePlan(plans.findByName(command.planName), command.planName)
        val raw = policy.createRawKey()
        val id = UUID.randomUUID()
        keys.insert(id, command.userId, plan.id, policy.hash(raw), raw.take(16), command.label)
        metrics.apiKeysCreated.increment()
        return ApiKeyCreatedResult(id, raw, command.label, command.planName, OffsetDateTime.now().toString())
    }
}
