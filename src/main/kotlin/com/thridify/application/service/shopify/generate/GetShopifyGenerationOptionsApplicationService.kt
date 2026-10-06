package com.thridify.application.service.shopify.generate

import com.thridify.domain.generation.GenerationProviderRegistry
import com.thridify.domain.shopify.ShopifyAccessPolicy
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import org.springframework.stereotype.Service

@Service
class GetShopifyGenerationOptionsApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val access: ShopifyAccessPolicy, private val providers: GenerationProviderRegistry) {
    fun execute(query: ShopifyGenerationOptionsQuery): ShopifyGenerationOptionsResult {
        access.connected(stores.findByDomain(sessions.verify(query.idToken).shopDomain))
        val provider = providers.current()
        return ShopifyGenerationOptionsResult(provider.name, 1, provider.maxInputImages)
    }
}

data class ShopifyGenerationOptionsQuery(val idToken: String)
data class ShopifyGenerationOptionsResult(val provider: String, val minImages: Int, val maxImages: Int?)
