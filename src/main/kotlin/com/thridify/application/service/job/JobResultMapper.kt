package com.thridify.application.service.job

import com.thridify.application.service.job.JobResult
import com.thridify.domain.job.JobEntity

internal fun JobEntity.toResult() = JobResult(
    id, status, inputImage1, inputImage2, outputGlbUrl, outputUsdzUrl,
    errorMessage, createdAt.toString(), completedAt?.toString(),
)
