package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyBillingClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
@EnableConfigurationProperties(ShopifyProperties::class)
class ShopifyConfiguration {
    @Bean
    fun shopifyBillingClient(config: ShopifyProperties, http: ShopifyHttpClient, environment: Environment): ShopifyBillingClient = when (config.billingMode) {
        "shopify" -> ShopifyPartnerBillingClient(config, http)

        "local-test" -> {
            require(environment.activeProfiles.toSet() == setOf("local")) { "Shopify local-test billing requires only the explicit local Spring profile" }
            LocalTestShopifyBillingClient(config)
        }

        else -> error("Unsupported Shopify billing mode; use shopify or local-test")
    }
}
