package com.thridify.architecture

import com.thridify.IntegrationTestBase
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.test.context.BootstrapWith
import java.lang.reflect.Modifier
import kotlin.test.assertTrue

class IntegrationTestArchitectureTest {
    @Test
    fun `all Spring integration tests inherit the container setup and integration tag`() {
        val springTests = ClassFileImporter().importPackages("com.thridify").map { it.reflect() }.filter {
            !Modifier.isAbstract(it.modifiers) &&
                AnnotatedElementUtils.hasAnnotation(it, BootstrapWith::class.java)
        }
        assertTrue(springTests.size >= 12, "Discovery must include the existing integration suite")
        springTests.forEach {
            assertTrue(IntegrationTestBase::class.java.isAssignableFrom(it), "${it.name} must extend IntegrationTestBase to use Testcontainers")
            assertTrue(it.getAnnotationsByType(Tag::class.java).any { tag -> tag.value == "integration" }, "${it.name} must inherit the integration tag")
        }
    }
}
