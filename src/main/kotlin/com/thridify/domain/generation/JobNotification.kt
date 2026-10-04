package com.thridify.domain.generation

import java.util.UUID

data class JobNotification(val jobId: UUID, val status: String, val glbUrl: String?, val usdzUrl: String?)
