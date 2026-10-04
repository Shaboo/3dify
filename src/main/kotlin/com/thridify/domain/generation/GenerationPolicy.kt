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
    fun inputKey(filename: String?) = "inputs/${UUID.randomUUID()}_$filename"
    fun matchesTask(actualJobId: UUID?, expectedJobId: UUID) = actualJobId == expectedJobId
    fun callbackOutcome(status: String, hasOutput: Boolean, glb: String?, usdz: String?): GenerationOutcome = when {
        status == "COMPLETED" && hasOutput -> GenerationOutcome.Succeeded(glb ?: "", usdz ?: "")
        status == "FAILED" -> GenerationOutcome.Failed("GPU Provider reported failure")
        else -> GenerationOutcome.InProgress
    }
}
