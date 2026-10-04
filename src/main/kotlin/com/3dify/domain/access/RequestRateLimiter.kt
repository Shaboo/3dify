package com.`3dify`.domain.access

import java.util.UUID

interface RequestRateLimiter {
    fun isAllowed(apiKeyId: UUID, maxRequestsPerMinute: Int): Boolean
}
