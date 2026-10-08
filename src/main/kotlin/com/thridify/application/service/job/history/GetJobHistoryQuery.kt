package com.thridify.application.service.job.history

import java.util.UUID

data class GetJobHistoryQuery(val jobId: UUID, val callerId: UUID, val apiKey: Boolean = false)
