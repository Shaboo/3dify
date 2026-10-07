package com.thridify.interfaces.rest

import com.thridify.application.service.generation.options.GetGenerationOptionsApplicationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GenerationOptionsController(private val options: GetGenerationOptionsApplicationService) {
    @GetMapping("/dashboard/generation-options")
    fun getOptions(): GenerationOptionsResponse = options.execute().let {
        GenerationOptionsResponse(it.provider, it.minImages, it.maxImages)
    }
}

data class GenerationOptionsResponse(val provider: String, val minImages: Int, val maxImages: Int?)
