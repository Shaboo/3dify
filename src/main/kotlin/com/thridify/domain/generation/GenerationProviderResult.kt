package com.thridify.domain.generation

sealed interface GenerationProviderResult {
    data object Pending : GenerationProviderResult
    data class Succeeded(val glbUrl: String, val usdzUrl: String) : GenerationProviderResult
    data class Failed(val message: String = "Generation provider could not produce the model") : GenerationProviderResult
}

open class GenerationProviderException(val ambiguous: Boolean, message: String, val retryable: Boolean = false, val retryAfterSeconds: Long = 30) : RuntimeException(message)
