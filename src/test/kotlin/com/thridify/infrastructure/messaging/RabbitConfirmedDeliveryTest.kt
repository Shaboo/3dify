package com.thridify.infrastructure.messaging

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RabbitConfirmedDeliveryTest : IntegrationTestBase() {
    private class Rabbit : GenericContainer<Rabbit>("rabbitmq:3-management-alpine")

    @Test
    fun `real broker confirms a routed message and rejects unroutable or missing exchanges`() {
        Rabbit().withEnv("RABBITMQ_DEFAULT_USER", "audit").withEnv("RABBITMQ_DEFAULT_PASS", "audit")
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1)).withStartupTimeout(Duration.ofSeconds(60)).use { broker ->
                broker.start()
                val factory = CachingConnectionFactory(broker.host, broker.getMappedPort(5672))
                factory.username = "audit"
                factory.setPassword("audit")
                factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
                factory.setPublisherReturns(true)
                try {
                    val template = RabbitTemplate(factory)
                    val admin = RabbitAdmin(factory)
                    val exchange = DirectExchange("audit.exchange")
                    val queue = Queue("audit.queue")
                    admin.declareExchange(exchange)
                    admin.declareQueue(queue)
                    admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("generate"))
                    val producer = TaskProducer(template, jacksonObjectMapper(), exchange.name, "generate")
                    val id = UUID.randomUUID()
                    producer.sendTask(id, listOf("one"))
                    val json = template.receiveAndConvert(queue.name, 5000) as String
                    assertEquals(id.toString(), jacksonObjectMapper().readTree(json).path("jobId").asText())
                    admin.deleteQueue(queue.name)
                    assertFailsWith<IllegalStateException> { producer.sendTask(UUID.randomUUID(), listOf("one")) }
                    assertFailsWith<Exception> { TaskProducer(template, jacksonObjectMapper(), "missing.exchange", "generate").sendTask(UUID.randomUUID(), listOf("one")) }
                } finally {
                    factory.destroy()
                }
            }
    }
}
