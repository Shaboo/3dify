package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.databind.JsonNode
import com.thridify.domain.shopify.ShopifyAttachedModel
import com.thridify.domain.shopify.ShopifyAttachmentException
import com.thridify.domain.shopify.ShopifyPhotoData
import com.thridify.domain.shopify.ShopifyProduct
import com.thridify.domain.shopify.ShopifyProductClient
import com.thridify.domain.shopify.ShopifyProductPhoto
import com.thridify.domain.shopify.ShopifySession
import com.thridify.domain.shopify.ShopifyStore
import com.thridify.infrastructure.storage.GenerationAssetDownloader
import com.thridify.shared.exception.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.UUID

@Component
class ShopifyProductAdminClient(private val config: ShopifyProperties, private val http: ShopifyHttpClient, private val tokens: ShopifyOfflineTokens, private val downloads: GenerationAssetDownloader, private val s3: S3Client, @Value("\${omni3d.r2.bucket}") private val bucket: String, @Value("\${omni3d.r2.public-url:}") private val publicUrl: String, @Value("\${spring.servlet.multipart.max-request-size:85MB}") private val maxRequestSize: org.springframework.util.unit.DataSize, uploadHttp: RestClient? = null) : ShopifyProductClient {
    private val uploads = uploadHttp ?: RestClient.builder().requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()).apply { setReadTimeout(Duration.ofMinutes(5)) }).build()
    override fun product(session: ShopifySession, idToken: String, productId: String, requireWrite: Boolean): ShopifyProduct {
        config.requireEnabled()
        val form = mapOf("client_id" to config.clientId, "client_secret" to config.clientSecret, "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange", "subject_token" to idToken, "subject_token_type" to "urn:ietf:params:oauth:token-type:id_token", "requested_token_type" to "urn:shopify:params:oauth:token-type:online-access-token")
            .entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
        val authorization = http.post("https://${session.shopDomain}/admin/oauth/access_token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), form)
        val access = authorization.path("access_token").asText()
        if (access.isBlank()) throw ApiException(401, "Shopify authorization expired; reopen the app")
        if (requireWrite && "write_products" !in authorization.path("associated_user_scope").asText().split(',')) throw ApiException(403, "Your Shopify account needs permission to update products")
        var cursor: String? = null
        var title = ""
        val photos = mutableListOf<ShopifyProductPhoto>()
        do {
            val product = graphql(session.shopDomain, access, PRODUCT_QUERY, mapOf("id" to productId, "after" to cursor)).path("product")
            if (product.path("id").asText() != productId) throw ApiException(404, "This product was not found in your store")
            title = product.path("title").asText()
            val media = product.path("media")
            for (node in media.path("nodes")) {
                val image = node.path("image")
                if (node.path("__typename").asText() == "MediaImage" && !image.isMissingNode && !image.isNull) photos.add(ShopifyProductPhoto(node.path("id").asText(), image.path("url").asText(), image.path("altText").takeIf { it.isTextual }?.asText()))
            }
            val next = media.path("pageInfo").path("endCursor").takeIf { it.isTextual }?.asText()
            if (media.path("pageInfo").path("hasNextPage").asBoolean() && (next == null || next == cursor)) throw ApiException(502, "Shopify returned invalid product pagination")
            cursor = if (media.path("pageInfo").path("hasNextPage").asBoolean()) next else null
        } while (cursor != null)
        return ShopifyProduct(productId, title, photos)
    }
    override fun downloadPhotos(product: ShopifyProduct, imageIds: List<String>): List<ShopifyPhotoData> {
        val byId = product.images.associateBy { it.id }
        val selected = imageIds.map { byId[it] ?: throw ApiException(400, "Choose photos that belong to this product") }
        var total = 0L
        return selected.map { photo ->
            val uri = URI(photo.url)
            if (uri.scheme != "https" || uri.host != "cdn.shopify.com" || uri.userInfo != null || uri.port !in setOf(-1, 443)) throw ApiException(502, "Shopify returned an unsupported product image URL")
            val file = Files.createTempFile("thridify-product-", ".image")
            try {
                downloads.download(uri, file, 20L * 1024 * 1024)
                total += Files.size(file)
                if (total > maxRequestSize.toBytes()) throw ApiException(400, "Selected product photos exceed the request size limit")
                val bytes = Files.readAllBytes(file)
                val type = when {
                    bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
                    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
                    bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                    else -> throw ApiException(400, "Choose JPEG, PNG or WebP product photos")
                }
                ShopifyPhotoData(bytes, "${photo.id.substringAfterLast('/')}.${type.substringAfter('/').replace("jpeg","jpg")}", type)
            } finally {
                Files.deleteIfExists(file)
            }
        }
    }
    override fun prepareBackground(store: ShopifyStore, idToken: String) {
        tokens.accessToken(store, idToken)
    }
    override fun findModel(store: ShopifyStore, productId: String, jobId: UUID): ShopifyAttachedModel? {
        val access = tokens.accessToken(store)
        var cursor: String? = null
        do {
            val product = try {
                graphql(store.shopDomain, access, MODEL_QUERY, mapOf("id" to productId, "after" to cursor)).path("product")
            } catch (ex: ApiException) {
                if (ex.statusCode == 401) tokens.invalidate(store, access)
                throw ex
            }
            if (product.path("id").asText() != productId) throw ShopifyAttachmentException(false, false, "The target product no longer exists")
            val media = product.path("media")
            media.path("nodes").firstOrNull { it.path("mediaContentType").asText() == "MODEL_3D" && it.path("alt").asText() == "3dify model $jobId" }?.let { return ShopifyAttachedModel(it.path("id").asText(), it.path("status").asText()) }
            val next = media.path("pageInfo").path("endCursor").takeIf { it.isTextual }?.asText()
            if (media.path("pageInfo").path("hasNextPage").asBoolean() && (next == null || next == cursor)) throw ApiException(502, "Shopify returned invalid media pagination")
            cursor = if (media.path("pageInfo").path("hasNextPage").asBoolean()) next else null
        } while (cursor != null)
        return null
    }
    override fun attachModel(store: ShopifyStore, productId: String, jobId: UUID, glbUrl: String): ShopifyAttachedModel {
        var mutationSent = false
        var usedAccess: String? = null
        val file = Files.createTempFile("thridify-attach-", ".glb")
        try {
            val expected = publicUrl.trimEnd('/') + "/outputs/$jobId/model.glb"
            if (glbUrl != expected || !expected.startsWith("https://") || bucket.isBlank()) throw ShopifyAttachmentException(false, false, "The model is not in managed storage")
            s3.getObject(GetObjectRequest.builder().bucket(bucket).key("outputs/$jobId/model.glb").build()).use { input -> Files.newOutputStream(file).use { output -> downloads.copy(input, output, 500L * 1024 * 1024) } }
            val access = tokens.accessToken(store)
            usedAccess = access
            val staged = graphql(store.shopDomain, access, STAGE_QUERY, mapOf("input" to listOf(mapOf("resource" to "MODEL_3D", "filename" to "3dify-$jobId.glb", "mimeType" to "model/gltf-binary", "httpMethod" to "POST", "fileSize" to Files.size(file).toString())))).path("stagedUploadsCreate")
            userErrors(staged)
            val target = staged.path("stagedTargets").firstOrNull() ?: throw ApiException(502, "Shopify returned no upload target")
            val uploadUri = URI(target.path("url").asText())
            val resource = target.path("resourceUrl").asText()
            trustedUpload(uploadUri)
            trustedUpload(URI(resource))
            val form = LinkedMultiValueMap<String, Any>()
            for (parameter in target.path("parameters")) form.add(parameter.path("name").asText(), parameter.path("value").asText())
            form.add(
                "file",
                object : FileSystemResource(file) {
                    override fun getFilename() = "3dify-$jobId.glb"
                },
            )
            uploads.post().uri(uploadUri).contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity()
            mutationSent = true
            val result = graphql(store.shopDomain, access, ATTACH_QUERY, mapOf("product" to mapOf("id" to productId), "media" to listOf(mapOf("originalSource" to resource, "mediaContentType" to "MODEL_3D", "alt" to "3dify model $jobId")))).path("productUpdate")
            userErrors(result)
            if (result.path("product").path("id").asText() != productId) throw ShopifyAttachmentException(true, true, "Shopify attachment acknowledgement is incomplete")
            return findModel(store, productId, jobId) ?: ShopifyAttachedModel("", "PROCESSING")
        } catch (ex: ShopifyAttachmentException) {
            throw ex
        } catch (ex: Exception) {
            if (ex is ApiException && ex.statusCode == 401) usedAccess?.let { tokens.invalidate(store, it) }
            val definitelyRejected = ex is ApiException && ex.statusCode in setOf(401, 403)
            throw ShopifyAttachmentException(mutationSent && !definitelyRejected, !definitelyRejected, "Shopify model attachment could not be completed")
        } finally {
            Files.deleteIfExists(file)
        }
    }
    private fun graphql(shop: String, access: String, query: String, variables: Map<String, Any?>): JsonNode = http.post("https://$shop/admin/api/${config.apiVersion}/graphql.json", mapOf("Content-Type" to "application/json", "X-Shopify-Access-Token" to access), mapOf("query" to query, "variables" to variables)).path("data")
    private fun userErrors(result: JsonNode) {
        if (!result.path("userErrors").isArray) throw ApiException(502, "Shopify returned no operation result")
        if (!result.path("userErrors").isEmpty) throw ShopifyAttachmentException(false, false, "Shopify rejected the product media; check the product and model file")
    }
    private fun trustedUpload(uri: URI) {
        val host = uri.host.orEmpty()
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) || !(host == "storage.googleapis.com" || host.endsWith(".storage.googleapis.com") || host.endsWith(".shopify.com") || host.endsWith(".shopifycdn.com") || host.endsWith(".amazonaws.com"))) throw ApiException(502, "Shopify returned an unsupported staged upload target")
    }
    private companion object {
        const val PRODUCT_QUERY = "query ProductPhotos(\$id: ID!, \$after: String) { product(id: \$id) { id title media(first: 100, after: \$after) { nodes { __typename id ... on MediaImage { image { url altText } } } pageInfo { hasNextPage endCursor } } } }"
        const val MODEL_QUERY = "query ExistingModel(\$id: ID!, \$after: String) { product(id: \$id) { id media(first: 100, after: \$after) { nodes { id alt status mediaContentType } pageInfo { hasNextPage endCursor } } } }"
        const val STAGE_QUERY = "mutation StageModel(\$input: [StagedUploadInput!]!) { stagedUploadsCreate(input: \$input) { stagedTargets { url resourceUrl parameters { name value } } userErrors { message } } }"
        const val ATTACH_QUERY = "mutation AttachModel(\$product: ProductUpdateInput!, \$media: [CreateMediaInput!]) { productUpdate(product: \$product, media: \$media) { product { id } userErrors { message } } }"
    }
}
