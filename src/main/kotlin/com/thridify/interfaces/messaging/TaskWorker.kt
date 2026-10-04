package com.thridify.interfaces.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.thridify.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.thridify.shared.message.GenerationTaskMessage
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class TaskWorker(private val mapper: ObjectMapper, private val dispatch: DispatchGenerationTaskApplicationService) {
    @RabbitListener(queues = ["\${omni3d.rabbitmq.queue}"])
    fun handleTask(messageJson: String) {
        val task = mapper.readValue(messageJson, GenerationTaskMessage::class.java)
        dispatch.execute(DispatchGenerationTaskCommand(task.jobId, task.inputImage1Key, task.inputImage2Key))
    }
}
