package com.thridify.application.service.job

import java.util.UUID

data class JobResult(
    val id: UUID,
    val status: String,
    val inputImage1: String,
    val inputImage2: String,
    val outputGlbUrl: String?,
    val outputUsdzUrl: String?,
    val errorMessage: String?,
    val createdAt: String,
    val completedAt: String?,
    val inputImages: List<String> = listOf(inputImage1, inputImage2),
    val productId: String? = null,
    val attachmentStatus: String? = null,
    val attachmentError: String? = null,
)
