package com.thridify.application.service

import com.thridify.application.service.generation.options.GetGenerationOptionsApplicationService
import com.thridify.domain.generation.GenerationProviderClient
import com.thridify.domain.generation.GenerationProviderRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenerationOptionsServiceTest {
    private val provider = mockk<GenerationProviderClient>()
    private val providers = mockk<GenerationProviderRegistry>()
    private val service = GetGenerationOptionsApplicationService(providers)

    @Test
    fun `options report the configured provider without submitting a task`() {
        every { providers.current() } returns provider
        every { provider.name } returns "meshy"
        every { provider.maxInputImages } returns 4
        val result = service.execute()
        assertEquals("meshy", result.provider)
        assertEquals(1, result.minImages)
        assertEquals(4, result.maxImages)
    }

    @Test
    fun `unlimited photo count remains null`() {
        every { providers.current() } returns provider
        every { provider.name } returns "alternative"
        every { provider.maxInputImages } returns null
        assertNull(service.execute().maxImages)
    }
}
