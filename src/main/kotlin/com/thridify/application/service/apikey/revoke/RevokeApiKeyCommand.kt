package com.thridify.application.service.apikey.revoke

import java.util.UUID

data class RevokeApiKeyCommand(val userId: UUID, val keyId: UUID)
