package com.thridify.interfaces.rest.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RequestLoggingFilterTest {
    @Test
    fun `request logging correlates denied requests and restores thread context`() {
        MDC.put("requestId", "parent")
        try {
            val request = MockHttpServletRequest("GET", "/private/secret")
            request.addHeader("X-Request-ID", "known-request")
            val response = MockHttpServletResponse()
            RequestLoggingFilter().doFilter(request, response) { _, _ ->
                assertEquals("known-request", MDC.get("requestId"))
                response.status = 401
            }
            assertEquals("known-request", response.getHeader("X-Request-ID"))
            assertEquals("parent", MDC.get("requestId"))
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `failure logs use route templates and redact raw paths query strings and unsafe request ids`() {
        val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
        val events = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(events)
        try {
            val request = MockHttpServletRequest("POST", "/webhook/secret-value")
            request.queryString = "token=secret-value"
            request.addHeader("X-Request-ID", "unsafe\nvalue")
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/webhook/{token}")
            val response = MockHttpServletResponse()
            assertFailsWith<IllegalStateException> {
                RequestLoggingFilter().doFilter(request, response) { _, _ -> throw IllegalStateException("secret-value") }
            }
            assertNotEquals("unsafe\nvalue", response.getHeader("X-Request-ID"))
            val message = events.list.single().formattedMessage
            assertTrue(message.contains("route=/webhook/{token} status=500"))
            assertFalse(message.contains("secret-value"))
            assertEquals(null, MDC.get("requestId"))
        } finally {
            logger.detachAppender(events)
            events.stop()
            MDC.clear()
        }
    }
}
