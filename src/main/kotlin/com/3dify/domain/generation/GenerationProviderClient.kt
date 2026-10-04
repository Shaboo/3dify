package com.`3dify`.domain.generation

import java.util.UUID

interface GenerationProviderClient {
    fun startGeneration(jobId: UUID, inputImage1: String, inputImage2: String?): String
}
