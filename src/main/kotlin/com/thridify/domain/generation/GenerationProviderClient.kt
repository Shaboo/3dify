package com.thridify.domain.generation

import java.util.UUID

interface GenerationProviderClient {
    val name: String

    /** Null means this provider has no photo-count ceiling. */
    val maxInputImages: Int? get() = null
    fun validateInputImages(count: Int) {
        if (count < 1 || maxInputImages?.let { count > it } == true) {
            throw com.thridify.shared.exception.BadRequestException("$name accepts 1–${maxInputImages ?: "unlimited"} photos; received $count")
        }
    }
    fun startGeneration(jobId: UUID, inputImages: List<String>): String
    fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String = startGeneration(jobId, listOfNotNull(inputImage1, inputImage2))
    fun retrieveTask(taskId: String): GenerationProviderResult
    fun deleteTask(taskId: String)
}
