package com.`3dify`.interfaces.messaging

import com.`3dify`.application.service.generation.dispatch.DispatchGenerationTaskApplicationService
import com.`3dify`.application.service.generation.dispatch.DispatchGenerationTaskCommand
import com.`3dify`.shared.message.GenerationTaskMessage
import com.fasterxml.jackson.databind.ObjectMapper
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
