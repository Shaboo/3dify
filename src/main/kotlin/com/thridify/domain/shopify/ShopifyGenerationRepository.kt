package com.thridify.domain.shopify

import com.thridify.domain.job.JobEntity
import java.util.UUID

interface ShopifyGenerationRepository {
    fun lock(store: ShopifyStore)
    fun findRequest(scopeId: UUID, requestId: UUID): JobEntity?
    fun insert(store: ShopifyStore, requestId: UUID, jobId: UUID, image1: String, image2: String): JobEntity
    fun insert(store: ShopifyStore, requestId: UUID, jobId: UUID, imageKeys: List<String>): JobEntity {
        require(imageKeys.size == 2) { "This repository does not support image lists" }
        return insert(store, requestId, jobId, imageKeys[0], imageKeys[1])
    }
    fun findJob(scopeId: UUID, jobId: UUID): JobEntity?
    fun listJobs(scopeId: UUID, before: UUID?): List<JobEntity>
}
