package com.thridify.infrastructure.shopify

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import java.util.UUID

class ShopifyInputDeletionClientTest {
    @Test
    fun `only managed outputs on the configured public storage origin are deleted`() {
        val s3 = mockk<S3Client>()
        every { s3.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse.builder().build()
        val client = ShopifyInputDeletionClient(s3, "models", "https://assets.example/public")
        val key = "outputs/${UUID.randomUUID()}/model.glb"
        client.deleteOutput("https://assets.example/public/$key?download=1")
        verify(exactly = 1) { s3.deleteObject(match<DeleteObjectRequest> { it.bucket() == "models" && it.key() == key }) }
        for (url in listOf("https://attacker.example/public/$key", "https://assets.example/other/$key", "https://assets.example/public/inputs/private.png", "https://assets.example/public/outputs/../../private.glb")) {
            assertThrows<IllegalStateException> { client.deleteOutput(url) }
        }
        verify(exactly = 1) { s3.deleteObject(any<DeleteObjectRequest>()) }
    }
}
