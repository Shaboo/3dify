package com.thridify.domain.generation

import com.thridify.domain.shopify.ShopifyWebhook
import java.util.UUID

interface GenerationProviderTaskRepository {
    fun reserve(jobId: UUID, provider: String): Boolean
    fun acknowledge(jobId: UUID, taskId: String)
    fun beginRetry(jobId: UUID, leaseId: UUID): Boolean
    fun retrySubmission(jobId: UUID, afterSeconds: Long)
    fun submissionFailed(jobId: UUID, uncertain: Boolean)
    fun claimDue(limit: Int): List<GenerationProviderTask>
    fun lock(jobId: UUID): GenerationProviderTask?
    fun canComplete(jobId: UUID): Boolean
    fun reschedule(jobId: UUID, leaseId: UUID)
    fun complete(jobId: UUID)
    fun forRedaction(event: ShopifyWebhook): List<GenerationProviderTask>
}
