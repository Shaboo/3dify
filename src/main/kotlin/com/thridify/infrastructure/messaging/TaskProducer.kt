package com.thridify.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.shared.message.GenerationTaskMessage
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TaskProducer(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${omni3d.rabbitmq.exchange}") private val exchange: String,
    @Value("\${omni3d.rabbitmq.routing-key}") private val routingKey: String,
) {

    fun sendTask(jobId: UUID, imageKeys: List<String>) {
        if (imageKeys.size == 2) return sendTask(jobId, imageKeys[0], imageKeys[1])
        val message = GenerationTaskMessage(jobId, imageKeys.first(), imageKeys.getOrElse(1) { imageKeys.first() }, imageKeys)
        rabbitTemplate.convertAndSend(exchange, routingKey, objectMapper.writeValueAsString(message))
    }

    fun sendTask(jobId: UUID, imageKey1: String, imageKey2: String) {
        val message = GenerationTaskMessage(
            jobId = jobId,
            inputImage1Key = imageKey1,
            inputImage2Key = imageKey2,
        )
        val json = objectMapper.writeValueAsString(message)
        rabbitTemplate.convertAndSend(exchange, routingKey, json)
    }
}
