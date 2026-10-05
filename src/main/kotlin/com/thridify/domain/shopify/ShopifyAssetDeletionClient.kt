package com.thridify.domain.shopify

interface ShopifyAssetDeletionClient {
    fun deleteInput(objectKey: String)
    fun deleteOutput(url: String)
}
