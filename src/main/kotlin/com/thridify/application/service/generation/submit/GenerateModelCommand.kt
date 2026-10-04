package com.thridify.application.service.generation.submit

import java.util.UUID

data class GenerateModelCommand(val apiKeyId: UUID, val image1: GenerationImage, val image2: GenerationImage)
