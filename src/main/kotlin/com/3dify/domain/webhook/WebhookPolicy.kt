package com.`3dify`.domain.webhook

import com.`3dify`.shared.exception.NotFoundException
import org.springframework.stereotype.Component

@Component
class WebhookPolicy {
    fun ensureDeleted(count: Int) {
        if (count == 0) throw NotFoundException("No webhook configured")
    }
}
