package com.thridify.domain.generation

import java.time.OffsetDateTime
import java.util.UUID

interface DirectGenerationRepository {
    fun lockAccount(apiKeyId: UUID): DirectGenerationAccount?
    fun findRequest(scopeId: UUID, requestId: UUID): DirectGenerationRequest?
    fun bindRequest(jobId: UUID, requestId: UUID, fingerprint: String)
    fun consume(scopeId: UUID, allowance: DirectGenerationAllowance): Boolean
}

data class DirectGenerationAccount(val scopeId: UUID, val provider: String, val status: String, val quota: Int, val periodStart: OffsetDateTime?, val periodEnd: OffsetDateTime?)
data class DirectGenerationAllowance(val start: OffsetDateTime, val end: OffsetDateTime, val limit: Int) {
    init {
        require(end.isAfter(start) && limit >= 0)
    }
}

data class DirectGenerationRequest(val jobId: UUID, val apiKeyId: UUID?, val status: String, val fingerprint: String?)
