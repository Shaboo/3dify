package com.`3dify`.shared.message

import java.util.UUID

/** Shared wire contract; neither adapter depends on the other. */
data class GenerationTaskMessage(val jobId: UUID, val inputImage1Key: String, val inputImage2Key: String)
