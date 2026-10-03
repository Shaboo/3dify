package com.omni3d.api.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class TaskProducer(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${omni3d.rabbitmq.exchange}") private val exchange: String,
    @Value("\${omni3d.rabbitmq.routing-key}") private val routingKey: String
) {

    data class TaskMessage(
        val jobId: UUID,
        val inputImage1Key: String,
        val inputImage2Key: String
    )

    fun sendTask(jobId: UUID, imageKey1: String, imageKey2: String) {
        val message = TaskMessage(
            jobId = jobId,
            inputImage1Key = imageKey1,
            inputImage2Key = imageKey2
        )
        val json = objectMapper.writeValueAsString(message)
        rabbitTemplate.convertAndSend(exchange, routingKey, json)
    }
}
