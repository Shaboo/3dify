package com.thridify.infrastructure.messaging

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.thridify.domain.outbox.OutboxRepository
import com.thridify.interfaces.messaging.TaskWorker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.util.UUID
import kotlin.test.assertEquals

class GenerationTaskContractTest {
    @Test
    fun `outbox publisher rabbit producer and listener preserve the task json contract`() {
        val mapper = jacksonObjectMapper()
        val id = UUID.randomUUID()
        val outbox: OutboxRepository = mockk()
        val stored = slot<String>()
        every { outbox.insert("JOB", id, capture(stored)) } just Runs
        OutboxGenerationTaskPublisher(outbox, mapper).enqueue(id, "image-1", "image-2")

        val rabbit: RabbitTemplate = mockk()
        val delivered = slot<String>()
        every { rabbit.convertAndSend("exchange", "routing-key", capture(delivered)) } just Runs
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
