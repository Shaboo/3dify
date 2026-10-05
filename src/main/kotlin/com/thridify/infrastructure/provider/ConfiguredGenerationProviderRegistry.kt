package com.thridify.infrastructure.provider

import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderRegistry
import org.springframework.stereotype.Component

@Component
class ConfiguredGenerationProviderRegistry(private val config: GenerationProviderProperties, clients: List<GenerationProviderClient>) : GenerationProviderRegistry {
    private val providers = clients.associateBy { it.name }
    init {
        require(providers.size == clients.size) { "Generation provider names must be unique" }
    }
    override fun current(): GenerationProviderClient = named(config.provider)
    override fun named(name: String): GenerationProviderClient = providers[name] ?: error("Generation provider is not configured: $name")
}
