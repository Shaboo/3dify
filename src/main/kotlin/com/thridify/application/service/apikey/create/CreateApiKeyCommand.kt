package com.thridify.application.service.apikey.create

import java.util.UUID

data class CreateApiKeyCommand(val userId: UUID, val label: String?, val planName: String)
