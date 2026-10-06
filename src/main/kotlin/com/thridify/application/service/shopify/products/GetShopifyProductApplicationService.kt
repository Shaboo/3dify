package com.thridify.application.service.shopify.products

import com.thridify.domain.shopify.ShopifyAccessPolicy
import com.thridify.domain.shopify.ShopifyProductClient
import com.thridify.domain.shopify.ShopifyProductPolicy
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import org.springframework.stereotype.Service

@Service
class GetShopifyProductApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val products: ShopifyProductClient, private val access: ShopifyAccessPolicy, private val policy: ShopifyProductPolicy) {
    fun execute(query: ShopifyProductQuery): ShopifyProductResult {
        val session = sessions.verify(query.idToken)
        access.connected(stores.findByDomain(session.shopDomain))
        val product = products.product(session, query.idToken, policy.productId(query.productId), false)
        return ShopifyProductResult(product.id, product.title, product.images.map { ShopifyProductPhotoResult(it.id, it.url, it.alt) })
    }
}

data class ShopifyProductQuery(val idToken: String, val productId: String)
data class ShopifyProductResult(val id: String, val title: String, val images: List<ShopifyProductPhotoResult>)
data class ShopifyProductPhotoResult(val id: String, val url: String, val alt: String?)
