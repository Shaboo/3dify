package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.domain.shopify.ShopifyAttachmentException
import com.thridify.domain.shopify.ShopifyProduct
import com.thridify.domain.shopify.ShopifyProductPhoto
import com.thridify.domain.shopify.ShopifySession
import com.thridify.domain.shopify.ShopifyStore
import com.thridify.infrastructure.storage.GenerationAssetDownloader
import com.thridify.shared.exception.ApiException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.unit.DataSize
import org.springframework.web.client.RestClient
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShopifyProductAdminClientTest {
    private val mapper = jacksonObjectMapper()
    private val http: ShopifyHttpClient = mockk()
    private val tokens: ShopifyOfflineTokens = mockk()
    private val s3: S3Client = mockk()
    private val config = ShopifyProperties(enabled = true, clientId = "client", clientSecret = "secret")
    private val store = ShopifyStore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "gid://shopify/Shop/1", "test.myshopify.com", true, OffsetDateTime.now())
    private fun client(downloads: GenerationAssetDownloader = GenerationAssetDownloader(), upload: RestClient? = null) = ShopifyProductAdminClient(config, http, tokens, downloads, s3, "bucket", "https://models.example", DataSize.ofMegabytes(85), upload)

    @Test
    fun `staff without product write access cannot authorize background generation`() {
        every { http.post(match { it.endsWith("/access_token") }, any(), any()) } returns mapper.readTree("""{"access_token":"online","associated_user_scope":"read_products","scope":"write_products"}""")
        assertEquals(403, assertThrows<ApiException> { client().product(ShopifySession(store.shopDomain, "42"), "id", "gid://shopify/Product/1", true) }.statusCode)
        verify(exactly = 0) { http.post(match { it.endsWith("graphql.json") }, any(), any()) }
    }

    @Test
    fun `product queries use the session shop and reject a missing or different product`() {
        every { http.post(match { it.endsWith("/access_token") }, any(), any()) } returns mapper.readTree("""{"access_token":"online","associated_user_scope":"write_products"}""")
        every { http.post("https://${store.shopDomain}/admin/api/2026-07/graphql.json", any(), any()) } returns mapper.readTree("""{"data":{"product":null}}""")
        assertEquals(404, assertThrows<ApiException> { client().product(ShopifySession(store.shopDomain, "42"), "id", "gid://shopify/Product/2", true) }.statusCode)
    }

    @Test
    fun `only selected images belonging to the product are downloaded in view order`() {
        val downloads: GenerationAssetDownloader = mockk()
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1)
        every { downloads.download(any(), any(), any()) } answers {
            Files.write(secondArg<Path>(), png)
        }
        val product = ShopifyProduct("gid://shopify/Product/1", "Product", listOf(ShopifyProductPhoto("one", "https://cdn.shopify.com/one.png", null), ShopifyProductPhoto("two", "https://cdn.shopify.com/two.png", null)))
        val images = client(downloads).downloadPhotos(product, listOf("two", "one"))
        assertEquals(listOf("two.png", "one.png"), images.map { it.filename })
        assertTrue(images.all { it.contentType == "image/png" })
        assertThrows<ApiException> { client(downloads).downloadPhotos(product, listOf("other-product-image")) }
        verify(exactly = 1) { downloads.download(URI("https://cdn.shopify.com/one.png"), any(), any()) }
        verify(exactly = 1) { downloads.download(URI("https://cdn.shopify.com/two.png"), any(), any()) }
        assertThrows<ApiException> { client(downloads).downloadPhotos(product.copy(images = listOf(ShopifyProductPhoto("hostile", "https://localhost/private", null))), listOf("hostile")) }
    }

    @Test
    fun `background upload stages exact owned GLB bytes and adds MODEL_3D media with job marker`() {
        val id = UUID.randomUUID()
        val bytes = byteArrayOf(1, 2, 3, 4)
        every { tokens.accessToken(store, null) } returns "offline"
        every { s3.getObject(any<GetObjectRequest>()) } answers { ResponseInputStream(GetObjectResponse.builder().build(), ByteArrayInputStream(bytes)) }
        every { http.post(any(), any(), any()) } answers {
            val payload = thirdArg<Map<String, Any>>()
            val query = payload["query"].toString()
            val variables = payload["variables"] as Map<*, *>
            when {
                query.contains("StageModel") -> {
                    val input = (variables["input"] as List<*>).single() as Map<*, *>
                    assertEquals("MODEL_3D", input["resource"])
                    assertEquals("4", input["fileSize"])
                    assertEquals("3dify-$id.glb", input["filename"])
                    mapper.readTree("""{"data":{"stagedUploadsCreate":{"stagedTargets":[{"url":"https://storage.googleapis.com/upload","resourceUrl":"https://storage.googleapis.com/model","parameters":[{"name":"key","value":"model.glb"}]}],"userErrors":[]}}}""")
                }

                query.contains("AttachModel") -> {
                    val media = (variables["media"] as List<*>).single() as Map<*, *>
                    assertEquals("MODEL_3D", media["mediaContentType"])
                    assertEquals("3dify model $id", media["alt"])
                    assertEquals("https://storage.googleapis.com/model", media["originalSource"])
                    mapper.readTree("""{"data":{"productUpdate":{"product":{"id":"gid://shopify/Product/1"},"userErrors":[]}}}""")
                }

                else -> mapper.readTree("""{"data":{"product":{"id":"gid://shopify/Product/1","media":{"nodes":[{"id":"model","alt":"3dify model $id","mediaContentType":"MODEL_3D","status":"READY"}],"pageInfo":{"hasNextPage":false}}}}}""")
            }
        }
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://storage.googleapis.com/upload")).andExpect(method(HttpMethod.POST))
            .andExpect { request -> assertTrue(request.headers.contentType?.isCompatibleWith(MediaType.MULTIPART_FORM_DATA) == true) }
            .andExpect { request ->
                val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsBytes
                assertTrue(body.toList().windowed(bytes.size).any { it == bytes.toList() })
                assertTrue(String(body, Charsets.ISO_8859_1).contains("3dify-$id.glb"))
            }.andRespond(withSuccess())
        val media = client(upload = builder.build()).attachModel(store, "gid://shopify/Product/1", id, "https://models.example/outputs/$id/model.glb")
        assertEquals("READY", media.status)
        verify { s3.getObject(match<GetObjectRequest> { it.key() == "outputs/$id/model.glb" && it.bucket() == "bucket" }) }
        server.verify()
    }

    @Test
    fun `an unowned model URL is rejected before storage or Shopify is contacted`() {
        val error = assertThrows<ShopifyAttachmentException> { client().attachModel(store, "gid://shopify/Product/1", UUID.randomUUID(), "https://attacker.example/model.glb") }
        assertFalse(error.ambiguous)
        assertFalse(error.retryable)
        verify(exactly = 0) { s3.getObject(any<GetObjectRequest>()) }
        verify(exactly = 0) { http.post(any(), any(), any()) }
    }
}
