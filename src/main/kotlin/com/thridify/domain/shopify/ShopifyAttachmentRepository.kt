package com.thridify.domain.shopify

import java.time.OffsetDateTime
import java.util.UUID

interface ShopifyAttachmentRepository {
    fun bind(store: ShopifyStore, jobId: UUID, productId: String)
    fun claimDue(limit: Int): List<ShopifyAttachmentTask>
    fun beginUpload(task: ShopifyAttachmentTask): Boolean
    fun finish(task: ShopifyAttachmentTask, status: String, mediaId: String?, error: String?)
}

data class ShopifyAttachmentTask(val jobId: UUID, val productId: String, val glbUrl: String, val store: ShopifyStore, val status: String, val attempts: Int, val leaseId: UUID, val leaseUntil: OffsetDateTime)
