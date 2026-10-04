package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyAssetDeletionClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

@Component
class ShopifyInputDeletionClient(private val s3: S3Client, @Value("\${omni3d.r2.bucket}") private val bucket: String) : ShopifyAssetDeletionClient {
    override fun deleteInput(objectKey: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build())
    }
}
