package com.thridify.application.service.generation.options

import com.thridify.domain.generation.GenerationProviderRegistry
import org.springframework.stereotype.Service

@Service
class GetGenerationOptionsApplicationService(private val providers: GenerationProviderRegistry) {
    fun execute(): GenerationOptionsResult {
        val provider = providers.current()
        return GenerationOptionsResult(provider.name, 1, provider.maxInputImages)
    }
}

data class GenerationOptionsResult(val provider: String, val minImages: Int, val maxImages: Int?)
