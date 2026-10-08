package com.thridify.application.service.generation.submit

import java.util.UUID

data class GenerateModelCommand(val apiKeyId: UUID, val images: List<GenerationImage>, val requestId: UUID = UUID.randomUUID()) {
    constructor(apiKeyId: UUID, image1: GenerationImage, image2: GenerationImage) : this(apiKeyId, listOf(image1, image2))
}
