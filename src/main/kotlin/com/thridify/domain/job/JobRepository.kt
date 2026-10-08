package com.thridify.domain.job

import com.thridify.domain.job.JobEntity
import java.util.UUID

interface JobRepository {
    fun insert(id: UUID, apiKeyId: UUID, imageKey1: String, imageKey2: String): Unit
    fun insert(id: UUID, apiKeyId: UUID, imageKeys: List<String>) {
        require(imageKeys.size == 2) { "This repository does not support image lists" }
        insert(id, apiKeyId, imageKeys[0], imageKeys[1])
    }
    fun updateStatus(jobId: UUID, status: String): Unit
    fun markSuccess(jobId: UUID, glbUrl: String, usdzUrl: String): Unit
    fun markFailed(jobId: UUID, errorMessage: String): Unit
    fun updateExternalTaskId(jobId: UUID, externalTaskId: String): Unit
    fun lock(jobId: UUID): JobEntity?
    fun findById(jobId: UUID): JobEntity?
    fun findByApiKey(jobId: UUID, apiKeyId: UUID): JobEntity?
    fun findByUser(jobId: UUID, userId: UUID): JobEntity?
    fun findByExternalTaskId(externalTaskId: String): JobEntity?
    fun findAllByApiKeyId(apiKeyId: UUID, before: UUID? = null): List<JobEntity>
    fun findAllByUserId(userId: UUID, before: UUID? = null): List<JobEntity>
    fun findUserIdByJobId(jobId: UUID): UUID?
}
