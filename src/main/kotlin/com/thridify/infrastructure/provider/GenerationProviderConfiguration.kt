package com.thridify.infrastructure.provider

import com.thridify.infrastructure.provider.meshy.MeshyProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GenerationProviderProperties::class, MeshyProperties::class)
class GenerationProviderConfiguration
