package com.thridify.infrastructure.storage

import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@Component
class GenerationAssetDownloader {
    fun download(url: URI, file: Path, maxBytes: Long) {
        val connection = url.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            check(connection.responseCode in 200..299) { "Generation output download failed" }
            check(connection.contentLengthLong <= maxBytes) { "Generation output exceeds storage limit" }
            connection.inputStream.use { input -> Files.newOutputStream(file).use { output -> copy(input, output, maxBytes) } }
        } finally {
            connection.disconnect()
        }
    }
    internal fun copy(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        val deadline = System.nanoTime() + 5 * 60 * 1_000_000_000L
        while (true) {
            check(System.nanoTime() < deadline) { "Generation output download timed out" }
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= maxBytes) { "Generation output exceeds storage limit" }
            output.write(buffer, 0, count)
        }
        check(total > 0) { "Generation output is empty" }
    }
}
