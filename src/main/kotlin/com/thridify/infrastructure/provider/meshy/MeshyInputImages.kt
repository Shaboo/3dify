package com.thridify.infrastructure.provider.meshy

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

@Component
class MeshyInputImages(private val s3: S3Client, @Value("\${omni3d.r2.bucket}") private val bucket: String) {
    fun dataUri(key: String): String {
        val bytes = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()).use { stream ->
            stream.readNBytes(20 * 1024 * 1024 + 1)
        }
        return encode(bytes)
    }
    internal fun encode(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024) { "Generation image is empty or exceeds 20 MB" }
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Generation image cannot be decoded" }
            val reader = readers.next()
            try {
                reader.input = input
                val format = reader.formatName.lowercase()
                require(format in setOf("png", "jpeg", "jpg", "webp")) { "Unsupported generation image format" }
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000) { "Generation image dimensions exceed the decode limit" }
                val normalized = if (format == "webp") {
                    ByteArrayOutputStream().use { output ->
                        check(ImageIO.write(reader.read(0), "png", output)) { "Could not convert WebP image" }
                        output.toByteArray()
                    }
                } else {
                    bytes
                }
                val mime = if (format in setOf("jpeg", "jpg")) "image/jpeg" else "image/png"
                return "data:$mime;base64,${Base64.getEncoder().encodeToString(normalized)}"
            } finally {
                reader.dispose()
            }
        }
    }
}
