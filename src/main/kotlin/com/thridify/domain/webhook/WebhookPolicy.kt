package com.thridify.domain.webhook

import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Component

@Component
class WebhookPolicy {
    fun destination(url: String): java.net.URI {
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            throw com.thridify.shared.exception.BadRequestException("Use a public HTTPS webhook URL")
        }
        val host = uri.host.orEmpty()
        if (url.length > 4096 || uri.scheme != "https" || uri.port !in setOf(-1, 443) || uri.userInfo != null || uri.fragment != null ||
            !host.contains('.') || host.contains(':') || host.matches(Regex("[0-9.]+"))
        ) {
            throw com.thridify.shared.exception.BadRequestException("Use a public HTTPS webhook URL")
        }
        return uri
    }

    fun ensureDeleted(count: Int) {
        if (count == 0) throw NotFoundException("No webhook configured")
    }
}
