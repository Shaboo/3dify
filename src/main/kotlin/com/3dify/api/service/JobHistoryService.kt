package com.omni3d.api.service

import com.omni3d.api.model.dto.JobHistoryEntry
import com.omni3d.api.repository.JobHistoryRepository
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
