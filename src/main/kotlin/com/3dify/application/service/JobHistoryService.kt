package com.omni3d.application.service

import com.omni3d.interfaces.rest.dto.JobHistoryEntry
import com.omni3d.infrastructure.persistence.JobHistoryRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class JobHistoryService(private val jobHistoryRepository: JobHistoryRepository) {

    fun recordChange(jobId: UUID, status: String, details: String? = null) {
        jobHistoryRepository.insert(jobId, status, details)
    }

    fun getHistory(jobId: UUID): List<JobHistoryEntry> =
        jobHistoryRepository.findAllByJobId(jobId)
            .map { h -> JobHistoryEntry(
                id        = h.id,
                jobId     = h.jobId,
                status    = h.status,
                details   = h.details,
                createdAt = h.createdAt.toString()
            )}
}
