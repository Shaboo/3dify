package com.thridify.infrastructure.persistence

import com.thridify.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import kotlin.test.assertTrue

class SchedulingIsolationTest : IntegrationTestBase() {
    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `integration context has no automatic scheduling`() {
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).isEmpty())
    }
}
