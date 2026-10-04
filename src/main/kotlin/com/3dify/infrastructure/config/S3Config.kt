package com.`3dify`.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
@ConditionalOnProperty("omni3d.r2.endpoint")
class S3Config(
    @Value("\${omni3d.r2.endpoint}") private val endpoint: String,
    @Value("\${omni3d.r2.access-key}") private val accessKey: String,
    @Value("\${omni3d.r2.secret-key}") private val secretKey: String,
) {

    @Bean
    @ConditionalOnMissingBean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey),
            ),
        )
        .region(Region.of("auto"))
        .forcePathStyle(true)
        .build()
}
