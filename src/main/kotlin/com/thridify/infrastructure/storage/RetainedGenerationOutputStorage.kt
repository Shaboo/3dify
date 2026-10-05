package com.thridify.infrastructure.storage

import com.thridify.domain.generation.GenerationOutputStorage
import com.thridify.domain.generation.RetainedGenerationOutputs
import com.thridify.infrastructure.provider.GenerationProviderProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Files
import java.util.UUID

@Component
class RetainedGenerationOutputStorage(private val s3: S3Client, @Value("\${omni3d.r2.bucket}") private val bucket: String, @Value("\${omni3d.r2.public-url:}") private val publicUrl: String, private val downloads: GenerationAssetDownloader, private val config: GenerationProviderProperties) : GenerationOutputStorage {
    override fun retain(jobId: UUID, provider: String, glbUrl: String, usdzUrl: String): RetainedGenerationOutputs {
        val base = URI(publicUrl.trimEnd('/') + "/")
        check(bucket.isNotBlank() && base.scheme == "https" && base.host != null && base.userInfo == null && base.query == null && base.fragment == null) { "Public model storage is not configured" }
        for ((format, source) in listOf("glb" to glbUrl, "usdz" to usdzUrl)) {
            val key = "outputs/$jobId/model.$format"
            val destination = base.resolve(key)
            if (URI(source) == destination) continue
            val url = URI(source)
            check(
                url.scheme == "https" && url.userInfo == null && url.port in setOf(-1, 443) && (url.host in config.assetHosts[provider].orEmpty() || (url.host == base.host && url.path == destination.path)),
            ) { "Generation output source is not trusted" }
            val file = Files.createTempFile("thridify-model-", ".$format")
            try {
                downloads.download(url, file, MAX_BYTES)
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(if (format == "glb") "model/gltf-binary" else "model/vnd.usdz+zip").build(), RequestBody.fromFile(file))
            } finally {
                Files.deleteIfExists(file)
            }
        }
        return RetainedGenerationOutputs(base.resolve("outputs/$jobId/model.glb").toString(), base.resolve("outputs/$jobId/model.usdz").toString())
    }
    override fun delete(jobId: UUID) {
        for (format in listOf("glb", "usdz")) s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key("outputs/$jobId/model.$format").build())
    }
    private companion object {
        const val MAX_BYTES = 500L * 1024 * 1024
    }
}
