package com.thridify.infrastructure.storage

import com.thridify.infrastructure.provider.GenerationProviderProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RetainedGenerationOutputStorageTest {
    @Test
    fun `provider outputs are copied byte-for-byte to owned canonical storage and temporary files removed`() {
        val s3 = mockk<S3Client>()
        val downloads = mockk<GenerationAssetDownloader>()
        val files = mutableListOf<Path>()
        every { downloads.download(any(), any(), any()) } answers {
            val file = secondArg<Path>()
            Files.write(file, byteArrayOf(1, 2, 3))
            files.add(file)
        }
        val keys = mutableListOf<String>()
        every { s3.putObject(any<PutObjectRequest>(), any<RequestBody>()) } answers {
            keys.add(firstArg<PutObjectRequest>().key())
            assertEquals(listOf<Byte>(1, 2, 3), secondArg<RequestBody>().contentStreamProvider().newStream().use { it.readAllBytes().toList() })
            PutObjectResponse.builder().build()
        }
        val id = UUID.randomUUID()
        val storage = RetainedGenerationOutputStorage(s3, "models", "https://models.example/public", downloads, GenerationProviderProperties())
        val result = storage.retain(id, "meshy", "https://assets.meshy.ai/task/model.glb?Expires=1", "https://assets.meshy.ai/task/model.usdz?Expires=1")
        assertEquals("https://models.example/public/outputs/$id/model.glb", result.glbUrl)
        assertEquals("https://models.example/public/outputs/$id/model.usdz", result.usdzUrl)
        assertEquals(listOf("outputs/$id/model.glb", "outputs/$id/model.usdz"), keys)
        files.forEach { assertFalse(Files.exists(it)) }
    }

    @Test
    fun `untrusted hosts or redirects cannot be supplied as output download sources`() {
        val s3 = mockk<S3Client>()
        val downloads = mockk<GenerationAssetDownloader>()
        val storage = RetainedGenerationOutputStorage(s3, "models", "https://models.example", downloads, GenerationProviderProperties())
        for (url in listOf("https://attacker.example/model.glb", "http://assets.meshy.ai/model.glb", "https://assets.meshy.ai:444/model.glb", "https://secret@assets.meshy.ai/model.glb")) {
            assertThrows<IllegalStateException> { storage.retain(UUID.randomUUID(), "meshy", url, "https://assets.meshy.ai/model.usdz") }
        }
        verify(exactly = 0) { downloads.download(any(), any(), any()) }
    }

    @Test
    fun `empty and oversized downloaded streams fail without buffering an entire model`() {
        val downloads = GenerationAssetDownloader()
        assertThrows<IllegalStateException> { downloads.copy(java.io.ByteArrayInputStream(byteArrayOf()), java.io.ByteArrayOutputStream(), 10) }
        assertThrows<IllegalStateException> { downloads.copy(java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3)), java.io.ByteArrayOutputStream(), 2) }
    }
}
