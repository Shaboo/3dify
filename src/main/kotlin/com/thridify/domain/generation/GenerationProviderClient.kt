package com.thridify.domain.generation

import java.util.UUID

interface GenerationProviderClient {
    val name: String
    fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String
    fun retrieveTask(taskId: String): GenerationProviderResult
    fun deleteTask(taskId: String)
}
