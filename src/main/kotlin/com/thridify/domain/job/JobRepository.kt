package com.thridify.domain.job

import com.thridify.domain.job.JobEntity
import java.util.UUID

interface JobRepository {
    fun insert(id: UUID, apiKeyId: UUID, imageKey1: String, imageKey2: String): Unit
    fun updateStatus(jobId: UUID, status: String): Unit
    fun markSuccess(jobId: UUID, glbUrl: String, usdzUrl: String): Unit
    fun markFailed(jobId: UUID, errorMessage: String): Unit
    fun updateExternalTaskId(jobId: UUID, externalTaskId: String): Unit
    fun findById(jobId: UUID): JobEntity?
    fun findByExternalTaskId(externalTaskId: String): JobEntity?
    fun findAllByApiKeyId(apiKeyId: UUID): List<JobEntity>
    fun findAllByUserId(userId: UUID): List<JobEntity>
    fun findUserIdByJobId(jobId: UUID): UUID?
}
