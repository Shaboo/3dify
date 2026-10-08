package com.thridify.domain.generation

import com.thridify.shared.exception.BadRequestException
import org.springframework.stereotype.Component
import java.util.UUID

sealed interface GenerationOutcome {
    data class Succeeded(val glbUrl: String, val usdzUrl: String) : GenerationOutcome
    data class Failed(val message: String) : GenerationOutcome
    data object InProgress : GenerationOutcome
}

@Component
class GenerationPolicy {
    fun ensureImagesPresent(firstSize: Int, secondSize: Int) {
        if (firstSize == 0 || secondSize == 0) throw BadRequestException("Both image1 and image2 are required")
    }
    fun ensureImagesPresent(sizes: List<Int>) {
        if (sizes.isEmpty() || sizes.any { it == 0 }) throw BadRequestException("At least one non-empty photo is required")
    }
    fun requestFingerprint(inputs: List<Pair<ByteArray, String>>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for ((bytes, type) in inputs) {
            val mime = type.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(8).putInt(bytes.size).putInt(mime.size).array())
            digest.update(bytes)
            digest.update(mime)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun ensureSameRequest(previous: DirectGenerationRequest, apiKeyId: UUID, fingerprint: String) {
        if (previous.apiKeyId != apiKeyId || previous.fingerprint != fingerprint) throw com.thridify.shared.exception.ConflictException("Idempotency key already belongs to a different generation request")
    }
    fun cleanupBefore(now: java.time.OffsetDateTime) = now.minusDays(1)
    fun inputKey(filename: String?) = "inputs/${UUID.randomUUID()}_$filename"
    fun matchesTask(actualJobId: UUID?, expectedJobId: UUID) = actualJobId == expectedJobId
    fun callbackOutcome(status: String, hasOutput: Boolean, glb: String?, usdz: String?): GenerationOutcome = when {
        status == "COMPLETED" && hasOutput && !glb.isNullOrBlank() && !usdz.isNullOrBlank() -> GenerationOutcome.Succeeded(glb, usdz)
        status == "COMPLETED" -> GenerationOutcome.Failed("GPU Provider returned incomplete model outputs")
        status in setOf("FAILED", "TIMED_OUT", "CANCELLED") -> GenerationOutcome.Failed("GPU Provider reported failure")
        else -> GenerationOutcome.InProgress
    }
}
