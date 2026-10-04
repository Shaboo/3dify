package com.thridify.infrastructure.storage

import com.thridify.domain.generation.ImageStorage
import com.thridify.shared.metrics.AppMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

@Service
class StorageService(
    private val s3Client: S3Client,
    private val metrics: AppMetrics,
    @Value("\${omni3d.r2.bucket}") private val bucket: String,
    @Value("\${omni3d.r2.endpoint}") private val endpoint: String,
) : ImageStorage {
    private val log = LoggerFactory.getLogger(StorageService::class.java)

    override fun upload(objectKey: String, data: ByteArray, contentType: String): String {
        log.debug("Uploading to R2 [key={}, bytes={}]", objectKey, data.size)
        return try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(data),
            )
            val url = "$endpoint/$bucket/$objectKey"
            metrics.storageUploads.increment()
            log.debug("Upload complete [key={}, url={}]", objectKey, url)
            url
        } catch (ex: Exception) {
            metrics.storageUploadErrors.increment()
            log.error("Upload failed [key={}]: {}", objectKey, ex.message, ex)
            throw ex
        }
    }

    fun upload(objectKey: String, inputStream: InputStream, contentLength: Long, contentType: String): String {
        log.debug("Uploading stream to R2 [key={}, length={}]", objectKey, contentLength)
        return try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build(),
                RequestBody.fromInputStream(inputStream, contentLength),
            )
            val url = "$endpoint/$bucket/$objectKey"
            metrics.storageUploads.increment()
            log.debug("Stream upload complete [key={}]", objectKey)
            url
        } catch (ex: Exception) {
            metrics.storageUploadErrors.increment()
            log.error("Stream upload failed [key={}]: {}", objectKey, ex.message, ex)
            throw ex
        }
    }

    fun download(objectKey: String): InputStream {
        log.debug("Downloading from R2 [key={}]", objectKey)
        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build(),
        )
    }
}
