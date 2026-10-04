package com.`3dify`.interfaces.scheduled

import com.`3dify`.application.service.generation.publish.PublishPendingGenerationTasksApplicationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxPublisher(private val publish: PublishPendingGenerationTasksApplicationService) {
    @Scheduled(fixedDelay = 500)
    fun pollAndPublish() = publish.execute()
}
