package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyAssetDeletionClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

@Component
class ShopifyInputDeletionClient(private val s3: S3Client, @Value("\${omni3d.r2.bucket}") private val bucket: String, @Value("\${omni3d.r2.public-url:}") private val publicUrl: String) : ShopifyAssetDeletionClient {
    override fun deleteOutput(url: String) {
        val base = java.net.URI(publicUrl.trimEnd('/') + "/")
        val output = java.net.URI(url)
        check(base.scheme == "https" && base.host != null && output.scheme == base.scheme && output.host == base.host && output.port == base.port && output.userInfo == null) { "Generated output storage ownership is not configured" }
        check(output.path.startsWith(base.path)) { "Generated output is outside the configured storage prefix" }
        val key = output.path.removePrefix(base.path)
        check(Regex("outputs/[0-9a-fA-F-]{36}/[a-zA-Z0-9._-]+\\.(glb|usdz)").matches(key)) { "Generated output does not use the managed output layout" }
        deleteInput(key)
    }

    override fun deleteInput(objectKey: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build())
    }
}
