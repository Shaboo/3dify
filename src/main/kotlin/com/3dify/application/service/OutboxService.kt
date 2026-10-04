package com.omni3d.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.infrastructure.persistence.OutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class OutboxService(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun saveEvent(aggregateType: String, aggregateId: UUID, payload: Any) {
        val json = objectMapper.writeValueAsString(payload)
        outboxRepository.insert(aggregateType, aggregateId, json)
    }
}
