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
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: GenerateModelCommand): GenerateResult {
        policy.ensureImagesPresent(command.image1.data.size, command.image2.data.size)
        val key1 = policy.inputKey(command.image1.filename)
        val key2 = policy.inputKey(command.image2.filename)
        storage.upload(key1, command.image1.data, command.image1.contentType)
        storage.upload(key2, command.image2.data, command.image2.contentType)
        return transactions.transaction {
            val id = UUID.randomUUID()
            val previousJobId = MDC.get("jobId")
            MDC.put("jobId", id.toString())
            try {
                jobs.insert(id, command.apiKeyId, key1, key2)
                history.insert(id, "PENDING", "Job created")
                publisher.publish(id, key1, key2)
                metrics.jobsCreated.increment()
                log.info("Job {} created and submitted for generation", id)
                GenerateResult(id, "PENDING")
            } finally {
                if (previousJobId == null) MDC.remove("jobId") else MDC.put("jobId", previousJobId)
            }
        }
    }
}
