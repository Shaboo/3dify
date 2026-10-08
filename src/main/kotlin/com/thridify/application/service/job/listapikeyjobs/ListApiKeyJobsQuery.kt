package com.thridify.application.service.job.listapikeyjobs

import java.util.UUID

data class ListApiKeyJobsQuery(val apiKeyId: UUID, val before: UUID? = null)
