package com.thridify.infrastructure.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.domain.generation.CustomerWebhookClient
import com.thridify.domain.generation.JobNotification
import com.thridify.domain.webhook.WebhookPolicy
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@Component
class HttpCustomerWebhookClient(private val mapper: ObjectMapper, private val policy: WebhookPolicy) : CustomerWebhookClient {
    override fun deliver(url: String, notification: JobNotification) {
        val uri = java.net.URI(policy.destination(url).toASCIIString())
        val addresses = InetAddress.getAllByName(uri.host)
        check(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "Webhook destination must resolve only to public addresses" }
        val body = mapper.writeValueAsBytes(mapOf("jobId" to notification.jobId.toString(), "status" to notification.status, "outputGlbUrl" to notification.glbUrl, "outputUsdzUrl" to notification.usdzUrl))
        // Connect to the checked address, retaining the hostname for TLS verification; DNS cannot change the destination afterward.
        Socket().use { socket ->
            socket.connect(InetSocketAddress(addresses.first(), 443), 5000)
            socket.soTimeout = 10000
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, uri.host, 443, true) as SSLSocket).use { tls ->
                tls.soTimeout = 10000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
                val headers = "POST $path HTTP/1.1\r\nHost: ${uri.host}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nX-3dify-Delivery-Id: ${notification.jobId}\r\nConnection: close\r\n\r\n"
                tls.outputStream.write(headers.toByteArray(Charsets.US_ASCII))
                tls.outputStream.write(body)
                tls.outputStream.flush()
                val status = StringBuilder()
                val deadline = System.nanoTime() + 10_000_000_000L
                val input = tls.inputStream
                while (true) {
                    check(status.length < 1024 && System.nanoTime() < deadline) { "Invalid or slow webhook response" }
                    val byte = input.read()
                    check(byte >= 0) { "Webhook response ended before its status" }
                    if (byte == 10) break
                    status.append(byte.toChar())
                }
                val code = status.toString().split(' ').getOrNull(1)?.toIntOrNull()
                check(code != null && code in 200..299) { "Webhook delivery was rejected" }
            }
        }
    }

    internal fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        return if (bytes.size == 4) {
            bytes[0] !in setOf(0, 10, 127) && bytes[0] < 224 &&
                !(bytes[0] == 100 && bytes[1] in 64..127) && !(bytes[0] == 169 && bytes[1] == 254) &&
                !(bytes[0] == 172 && bytes[1] in 16..31) && !(bytes[0] == 192 && bytes[1] in setOf(0, 168)) &&
                !(bytes[0] == 198 && bytes[1] in 18..19)
        } else {
            bytes[0] and 224 == 32 && !(bytes[0] == 32 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0)
        }
    }
}
