package com.thridify.interfaces.scheduled.generation

import com.thridify.application.service.generation.reconcile.ReconcileGenerationTasksApplicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["generation.polling-enabled"], havingValue = "true")
class GenerationReconciliationJob(private val reconcile: ReconcileGenerationTasksApplicationService) {
    @Scheduled(fixedDelayString = "\${generation.polling-delay-ms:15000}")
    fun run() = reconcile.execute()
}
