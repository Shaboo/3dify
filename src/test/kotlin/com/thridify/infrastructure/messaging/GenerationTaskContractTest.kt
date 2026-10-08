package com.thridify.infrastructure.messaging

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.thridify.infrastructure.outbox.OutboxGenerationTaskPublisher
import com.thridify.infrastructure.outbox.OutboxRepository
import com.thridify.interfaces.messaging.TaskWorker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.util.UUID
import kotlin.test.assertEquals

class GenerationTaskContractTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 3, 4, 100])
    fun `input list survives the actual outbox relay and worker`(count: Int) {
        val mapper = jacksonObjectMapper()
        val id = UUID.randomUUID()
        val keys = List(count) { "image-$it" }
        val outbox: OutboxRepository = mockk()
        val stored = slot<String>()
        every { outbox.insert("JOB", id, capture(stored)) } just Runs
        OutboxGenerationTaskPublisher(outbox, mapper).publish(id, keys)
        val rabbit: RabbitTemplate = mockk(relaxed = true)
        val delivered = slot<String>()
        every { rabbit.convertAndSend("exchange", "routing-key", capture(delivered), any<org.springframework.amqp.rabbit.connection.CorrelationData>()) } answers {
            arg<org.springframework.amqp.rabbit.connection.CorrelationData>(3).future.complete(org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(true, null))
            Unit
        }
        com.thridify.infrastructure.outbox.RabbitGenerationTaskDelivery(TaskProducer(rabbit, mapper, "exchange", "routing-key"), mapper).deliver(
            com.thridify.infrastructure.outbox.OutboxMessageEntity(UUID.randomUUID(), "JOB", id, stored.captured, java.time.OffsetDateTime.now(), null),
        )
        assertEquals(mapper.readTree(stored.captured), mapper.readTree(delivered.captured))
        val dispatch: DispatchGenerationTaskApplicationService = mockk()
        every { dispatch.execute(any()) } just Runs
        TaskWorker(mapper, dispatch).handleTask(delivered.captured)
        verify(exactly = 1) { dispatch.execute(DispatchGenerationTaskCommand(id, keys)) }
    }

    @Test
    fun `outbox publisher rabbit producer and listener preserve the task json contract`() {
        val mapper = jacksonObjectMapper()
        val id = UUID.randomUUID()
        val outbox: OutboxRepository = mockk()
        val stored = slot<String>()
        every { outbox.insert("JOB", id, capture(stored)) } just Runs
        OutboxGenerationTaskPublisher(outbox, mapper).publish(id, "image-1", "image-2")

        val rabbit: RabbitTemplate = mockk(relaxed = true)
        val delivered = slot<String>()
        every { rabbit.convertAndSend("exchange", "routing-key", capture(delivered), any<org.springframework.amqp.rabbit.connection.CorrelationData>()) } answers {
            arg<org.springframework.amqp.rabbit.connection.CorrelationData>(3).future.complete(org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(true, null))
            Unit
        }
        TaskProducer(rabbit, mapper, "exchange", "routing-key").sendTask(id, "image-1", "image-2")
        val expected = mapper.readTree("""{"jobId":"$id","inputImage1Key":"image-1","inputImage2Key":"image-2"}""")
        assertEquals(expected, mapper.readTree(stored.captured))
        assertEquals(expected, mapper.readTree(delivered.captured))

        val dispatch: DispatchGenerationTaskApplicationService = mockk()
        every { dispatch.execute(any()) } just Runs
        TaskWorker(mapper, dispatch).handleTask(delivered.captured)
        verify(exactly = 1) { dispatch.execute(DispatchGenerationTaskCommand(id, "image-1", "image-2")) }
    }
}
