package com.thridify.domain.shopify

import com.thridify.shared.exception.BadRequestException
import org.springframework.stereotype.Component

@Component
class ShopifyProductPolicy {
    fun productId(id: String): String {
        if (!Regex("gid://shopify/Product/[0-9]+").matches(id)) throw BadRequestException("Choose a saved Shopify product")
        return id
    }
    fun imageIds(ids: List<String>) {
        if (ids.isEmpty() || ids.distinct().size != ids.size || ids.any { !Regex("gid://shopify/MediaImage/[0-9]+").matches(it) }) throw BadRequestException("Choose distinct photos from this product")
    }
    fun sameProduct(existing: String?, requested: String?) {
        if (existing != requested) throw BadRequestException("This request ID belongs to another product; start a new request")
    }
}
