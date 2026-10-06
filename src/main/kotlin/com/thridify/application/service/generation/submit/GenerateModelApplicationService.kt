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
    private val providers: com.thridify.domain.generation.GenerationProviderRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: GenerateModelCommand): GenerateResult {
        policy.ensureImagesPresent(command.images.map { it.data.size })
        providers.current().validateInputImages(command.images.size)
        val keys = command.images.map { image ->
            val key = policy.inputKey(image.filename)
            storage.upload(key, image.data, image.contentType)
            key
        }
        return transactions.transaction {
            val id = UUID.randomUUID()
            val previousJobId = MDC.get("jobId")
            MDC.put("jobId", id.toString())
            try {
                jobs.insert(id, command.apiKeyId, keys)
                history.insert(id, "PENDING", "Job created")
                publisher.publish(id, keys)
                metrics.jobsCreated.increment()
                log.info("Job {} created and submitted for generation image_count={}", id, command.images.size)
                GenerateResult(id, "PENDING")
            } finally {
                if (previousJobId == null) MDC.remove("jobId") else MDC.put("jobId", previousJobId)
            }
        }
    }
}
