package com.thridify.infrastructure.shopify

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ShopifyProperties::class)
class ShopifyConfiguration
