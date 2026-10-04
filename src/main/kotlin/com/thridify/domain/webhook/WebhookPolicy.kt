package com.thridify.domain.webhook

import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Component

@Component
class WebhookPolicy {
    fun ensureDeleted(count: Int) {
        if (count == 0) throw NotFoundException("No webhook configured")
    }
}
