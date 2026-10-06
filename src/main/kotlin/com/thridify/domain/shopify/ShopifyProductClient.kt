package com.thridify.domain.shopify

import java.util.UUID

interface ShopifyProductClient {
    fun product(session: ShopifySession, idToken: String, productId: String, requireWrite: Boolean): ShopifyProduct
    fun downloadPhotos(product: ShopifyProduct, imageIds: List<String>): List<ShopifyPhotoData>
    fun prepareBackground(store: ShopifyStore, idToken: String)
    fun findModel(store: ShopifyStore, productId: String, jobId: UUID): ShopifyAttachedModel?
    fun attachModel(store: ShopifyStore, productId: String, jobId: UUID, glbUrl: String): ShopifyAttachedModel
}

data class ShopifyProduct(val id: String, val title: String, val images: List<ShopifyProductPhoto>)
data class ShopifyProductPhoto(val id: String, val url: String, val alt: String?)
data class ShopifyPhotoData(val data: ByteArray, val filename: String, val contentType: String)
data class ShopifyAttachedModel(val id: String, val status: String)
class ShopifyAttachmentException(val ambiguous: Boolean, val retryable: Boolean, message: String) : RuntimeException(message)
