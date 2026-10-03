package com.omni3d.api

import io.mockk.mockk
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import software.amazon.awssdk.services.s3.S3Client
import com.omni3d.api.service.RunPodClient

/**
 * Test-only Spring beans that replace infrastructure components
 * (RabbitMQ, S3) with MockK mocks so the application context
 * starts cleanly without real services running.
 */
@TestConfiguration
class TestBeanConfig {

    /** Replace the real RabbitTemplate with a MockK mock to avoid connection errors. */
    @Bean
    @Primary
    fun rabbitTemplate(): RabbitTemplate = mockk(relaxed = true)

    /** Replace the real RabbitMQ ConnectionFactory to stop listener startup errors. */
    @Bean
    @Primary
    fun rabbitConnectionFactory(): org.springframework.amqp.rabbit.connection.ConnectionFactory = mockk(relaxed = true)

    /** Replace the real S3Client with a MockK mock to avoid credential/endpoint errors. */
    @Bean
    @Primary
    fun s3Client(): S3Client = mockk(relaxed = true)

    /** Replace the RunPodClient with a MockK mock to avoid API calls and config issues. */
    @Bean
    @Primary
    fun runPodClient(): RunPodClient = mockk(relaxed = true)
}
