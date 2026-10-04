package com.`3dify`.application.service.generation.dispatch

import java.util.UUID

data class DispatchGenerationTaskCommand(val jobId: UUID, val imageKey1: String, val imageKey2: String)
