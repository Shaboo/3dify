package com.thridify.domain.generation

import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class GenerationPolicyTest {
    @Test
    fun `missing completed outputs and all provider terminal failures finish as failed`() {
        val policy = GenerationPolicy()
        for (status in listOf("FAILED", "TIMED_OUT", "CANCELLED")) {
            assertIs<GenerationOutcome.Failed>(policy.callbackOutcome(status, false, null, null))
        }
        assertIs<GenerationOutcome.Failed>(policy.callbackOutcome("COMPLETED", true, "", "https://assets/model.usdz"))
        assertIs<GenerationOutcome.Failed>(policy.callbackOutcome("COMPLETED", false, null, null))
        assertIs<GenerationOutcome.InProgress>(policy.callbackOutcome("IN_PROGRESS", false, null, null))
    }
}
