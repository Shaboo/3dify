package com.thridify.infrastructure.provider.meshy

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.s3.S3Client
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.assertTrue

class MeshyInputImagesTest {
    private val images = MeshyInputImages(mockk<S3Client>(), "bucket")

    @Test
    fun `PNG and JPEG stay supported and corrupt images cannot reach provider`() {
        for (format in listOf("png", "jpeg")) {
            val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, it) }.toByteArray()
            assertTrue(images.encode(bytes).startsWith("data:image/${if (format == "jpeg") "jpeg" else "png"};base64,"))
        }
        assertThrows<IllegalArgumentException> { images.encode(byteArrayOf(1, 2)) }
    }

    @Test
    fun `WebP is converted to a provider-supported PNG`() {
        val bytes = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA")
        assertTrue(images.encode(bytes).startsWith("data:image/png;base64,"))
    }
}
