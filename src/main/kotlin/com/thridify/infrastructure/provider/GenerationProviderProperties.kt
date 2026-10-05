package com.thridify.infrastructure.provider

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("generation")
data class GenerationProviderProperties(var provider: String = "meshy", var pollingEnabled: Boolean = false, var assetHosts: Map<String, List<String>> = mapOf("meshy" to listOf("assets.meshy.ai")))
