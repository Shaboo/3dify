package com.omni3d.api.config

import org.springframework.amqp.core.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig(
    @Value("\${omni3d.rabbitmq.exchange}") private val exchangeName: String,
    @Value("\${omni3d.rabbitmq.queue}") private val queueName: String,
    @Value("\${omni3d.rabbitmq.routing-key}") private val routingKey: String
) {

    @Bean
    fun taskExchange(): DirectExchange = DirectExchange(exchangeName, true, false)

    @Bean
    fun taskQueue(): Queue = QueueBuilder.durable(queueName).build()

    @Bean
    fun taskBinding(taskQueue: Queue, taskExchange: DirectExchange): Binding =
        BindingBuilder.bind(taskQueue).to(taskExchange).with(routingKey)
}
