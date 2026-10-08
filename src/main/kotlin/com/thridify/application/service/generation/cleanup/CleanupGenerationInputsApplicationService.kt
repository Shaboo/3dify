package com.thridify.application.service.generation.cleanup

import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.ImageStorage
import com.thridify.domain.generation.PendingInputRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class CleanupGenerationInputsApplicationService(private val pending: PendingInputRepository, private val storage: ImageStorage, private val policy: GenerationPolicy) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute() {
        for (key in pending.expired(policy.cleanupBefore(OffsetDateTime.now()))) {
            try {
                storage.delete(key)
                pending.release(listOf(key))
            } catch (ex: Exception) {
                log.warn("Uncommitted generation input cleanup remains pending error_type={}", ex.javaClass.simpleName)
            }
        }
    }
}
