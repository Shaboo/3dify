package com.thridify.application.service.generation.submit

import com.thridify.application.service.generation.submit.GenerateResult
import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.GenerationTaskPublisher
import com.thridify.domain.generation.ImageStorage
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.job.JobRepository
import com.thridify.domain.transaction.TransactionProvider
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GenerateModelApplicationService(
    private val storage: ImageStorage,
    private val jobs: JobRepository,
    private val history: JobHistoryRepository,
    private val publisher: GenerationTaskPublisher,
    private val transactions: TransactionProvider,
    private val policy: GenerationPolicy,
    private val metrics: AppMetrics,
    private val direct: com.thridify.domain.generation.DirectGenerationRepository,
    private val allowances: com.thridify.domain.generation.DirectGenerationAllowancePolicy,
    private val pending: com.thridify.domain.generation.PendingInputRepository,
    private val providers: com.thridify.domain.generation.GenerationProviderRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: GenerateModelCommand): GenerateResult {
        policy.ensureImagesPresent(command.images.map { it.data.size })
        val fingerprint = policy.requestFingerprint(command.images.map { it.data to it.contentType })
        val previous = transactions.transaction {
            val account = direct.lockAccount(command.apiKeyId)
            allowances.allowance(account, java.time.OffsetDateTime.now())
            direct.findRequest(requireNotNull(account).scopeId, command.requestId)
        }
        previous?.let {
            policy.ensureSameRequest(it, command.apiKeyId, fingerprint)
            return GenerateResult(it.jobId, it.status)
        }
        providers.current().validateInputImages(command.images.size)
        val keys = command.images.map { policy.inputKey(it.filename) }
        transactions.transaction { pending.record(keys) }
        val id = UUID.randomUUID()
        var committed = false
        try {
            command.images.zip(keys).forEach { (image, key) -> storage.upload(key, image.data, image.contentType) }
            val result = transactions.transaction {
                val account = direct.lockAccount(command.apiKeyId)
                val allowance = allowances.allowance(account, java.time.OffsetDateTime.now())
                val duplicate = direct.findRequest(requireNotNull(account).scopeId, command.requestId)
                if (duplicate != null) {
                    policy.ensureSameRequest(duplicate, command.apiKeyId, fingerprint)
                    return@transaction GenerateResult(duplicate.jobId, duplicate.status)
                }
                allowances.ensureConsumed(direct.consume(requireNotNull(account).scopeId, allowance))
                val previousJobId = MDC.get("jobId")
                MDC.put("jobId", id.toString())
                try {
                    jobs.insert(id, command.apiKeyId, keys)
                    direct.bindRequest(id, command.requestId, fingerprint)
                    history.insert(id, "PENDING", "Job created")
                    publisher.publish(id, keys)
                    pending.release(keys)
                    metrics.jobsCreated.increment()
                    log.info("Job {} created and submitted for generation image_count={}", id, command.images.size)
                    GenerateResult(id, "PENDING")
                } finally {
                    if (previousJobId == null) MDC.remove("jobId") else MDC.put("jobId", previousJobId)
                }
            }
            committed = result.jobId == id
            return result
        } finally {
            if (!committed) {
                keys.forEach { key ->
                    runCatching {
                        storage.delete(key)
                        pending.release(listOf(key))
                    }.onFailure { log.warn("Uncommitted generation upload remains queued for cleanup error_type={}", it.javaClass.simpleName) }
                }
            }
        }
    }
}
