package com.thridify.domain.generation

interface GenerationProviderRegistry {
    fun current(): GenerationProviderClient
    fun named(name: String): GenerationProviderClient
}
