package com.`3dify`.application.service.generation.callback

import java.util.UUID

data class HandleGenerationCallbackCommand(
    val jobId: UUID,
    val externalTaskId: String,
    val status: String,
    val hasOutput: Boolean,
    val glbUrl: String?,
    val usdzUrl: String?,
)
