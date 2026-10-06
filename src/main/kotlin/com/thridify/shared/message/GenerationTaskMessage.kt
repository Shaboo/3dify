package com.thridify.shared.message

import java.util.UUID

/** Shared wire contract; neither adapter depends on the other. */
data class GenerationTaskMessage(
    val jobId: UUID,
    val inputImage1Key: String,
    val inputImage2Key: String,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val imageKeys: List<String>? = null,
)
