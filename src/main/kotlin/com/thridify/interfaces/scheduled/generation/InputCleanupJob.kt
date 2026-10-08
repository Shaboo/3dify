package com.thridify.interfaces.scheduled.generation

import com.thridify.application.service.generation.cleanup.CleanupGenerationInputsApplicationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class InputCleanupJob(private val cleanup: CleanupGenerationInputsApplicationService) {
    @Scheduled(fixedDelay = 60000)
    fun run() = cleanup.execute()
}
