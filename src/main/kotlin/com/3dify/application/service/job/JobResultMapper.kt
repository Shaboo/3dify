package com.`3dify`.application.service.job

import com.`3dify`.application.service.job.JobResult
import com.`3dify`.domain.job.JobEntity

internal fun JobEntity.toResult() = JobResult(
    id, status, inputImage1, inputImage2, outputGlbUrl, outputUsdzUrl,
    errorMessage, createdAt.toString(), completedAt?.toString(),
)
