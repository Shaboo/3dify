package com.thridify.infrastructure.provider.meshy

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("generation.meshy")
data class MeshyProperties(var enabled: Boolean = false, var apiKey: String = "", var aiModel: String = "meshy-7.1", var requestIntervalMs: Long = 500)
