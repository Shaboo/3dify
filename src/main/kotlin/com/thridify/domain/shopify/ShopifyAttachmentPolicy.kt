package com.thridify.domain.shopify
import org.springframework.stereotype.Component
data class ShopifyAttachmentDecision(val status: String, val error: String?)

@Component
class ShopifyAttachmentPolicy {
    fun mayUpload(status: String) = status in setOf("waiting", "retrying")
    fun observed(media: ShopifyAttachedModel?, attempts: Int): ShopifyAttachmentDecision {
        val status = when {
            media?.status == "READY" -> "attached"
            media?.status == "FAILED" -> "failed"
            attempts >= 120 -> "failed"
            media != null -> "processing"
            else -> "checking"
        }
        val error = when {
            media?.status == "FAILED" -> "Shopify could not process the model"
            status == "failed" -> "Attachment needs review in Shopify; automatic creation is paused to prevent duplicate media"
            status == "checking" -> "Checking whether Shopify accepted the model"
            else -> null
        }
        return ShopifyAttachmentDecision(status, error)
    }
    fun failed(status: String, attempts: Int, uploadStarted: Boolean, error: Exception): ShopifyAttachmentDecision {
        // A possibly accepted upload must be checked before another creation attempt.
        val ambiguous = uploadStarted && (error !is ShopifyAttachmentException || error.ambiguous)
        val next = when {
            ambiguous -> "checking"
            status == "checking" -> if (attempts >= 120) "failed" else "checking"
            error is ShopifyAttachmentException && !error.retryable -> "failed"
            attempts >= 10 -> "failed"
            else -> "retrying"
        }
        return ShopifyAttachmentDecision(next, if (next == "failed") "Could not attach the model; reopen the app and check the product" else "Shopify attachment will be checked again")
    }
}
