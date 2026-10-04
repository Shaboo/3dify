package com.thridify.domain.shopify

import com.thridify.domain.job.JobEntity
import java.util.UUID

interface ShopifyGenerationRepository {
    fun findRequest(scopeId: UUID, requestId: UUID): JobEntity?
    fun insert(store: ShopifyStore, requestId: UUID, jobId: UUID, image1: String, image2: String): JobEntity
    fun findJob(scopeId: UUID, jobId: UUID): JobEntity?
    fun listJobs(scopeId: UUID, before: UUID?): List<JobEntity>
}
