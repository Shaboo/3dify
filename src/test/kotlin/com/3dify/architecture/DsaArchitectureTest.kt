package com.`3dify`.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestMapping

class DsaArchitectureTest {
    private val production = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.3dify")

    @Test
    fun `dependencies point inward`() {
        check(production.any { it.name == "com.3dify.application.service.subscription.checkout.CreateCheckoutSessionApplicationService" })
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Interfaces").definedBy("com.3dify.interfaces..")
            .layer("Application").definedBy("com.3dify.application.service..")
            .layer("Domain").definedBy("com.3dify.domain..")
            .layer("Infrastructure").definedBy("com.3dify.infrastructure..")
            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .check(production)
    }

    @Test
    fun `core has no technology dependencies`() {
        noClasses().that().resideInAnyPackage("com.3dify.domain..", "com.3dify.application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.jooq..", "org.springframework.transaction..", "org.springframework.web..",
                "org.springframework.http..", "org.springframework.amqp..", "software.amazon.awssdk..",
                "com.fasterxml.jackson..", "com.stripe..", "io.jsonwebtoken..", "io.github.bucket4j..",
                "org.springframework.security..", "javax.sql..", "org.springframework.jdbc..",
                "jakarta.persistence..", "javax.persistence..", "jakarta.xml.bind..",
                "org.springframework.beans.factory.annotation..", "org.springframework.boot.context.properties..",
            ).check(production)
    }

    @Test
    fun `application services own exactly one use case and never depend on another`() {
        classes().that().resideInAPackage("com.3dify.application.service..")
            .and().areAnnotatedWith(Service::class.java)
            .should().haveSimpleNameEndingWith("ApplicationService")
            .andShould(object : ArchCondition<JavaClass>("own one public use case without service chaining") {
                override fun check(item: JavaClass, events: ConditionEvents) {
                    val methods = item.methods.filter { it.modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC) }
                    events.add(
                        SimpleConditionEvent(
                            item,
                            methods.size == 1 && methods.singleOrNull()?.name == "execute",
                            "${item.name} must declare exactly one public execute method",
                        ),
                    )
                    item.directDependenciesFromSelf.filter {
                        it.targetClass.simpleName.endsWith("ApplicationService") && it.targetClass.name != item.name
                    }.forEach { events.add(SimpleConditionEvent.violated(it, it.description)) }
                }
            }).check(production)
    }

    @Test
    fun `entry points invoke one application service`() {
        val methods = production.flatMap { it.methods }.filter { method ->
            method.annotations.any { annotation ->
                annotation.rawType.name in setOf(RabbitListener::class.java.name, Scheduled::class.java.name) ||
                    annotation.rawType.name == RequestMapping::class.java.name ||
                    annotation.rawType.isAnnotatedWith(RequestMapping::class.java)
            } || (method.owner.packageName.contains(".interfaces.rest.filter") && method.name == "doFilterInternal")
        }
        check(methods.size >= 20) { "Entry point discovery must not silently become empty" }
        methods.forEach { method ->
            val calls = method.methodCallsFromSelf.filter { it.target.owner.simpleName.endsWith("ApplicationService") }
            check(calls.size == 1) { "${method.fullName} must invoke exactly one application service; found ${calls.size}" }
        }
    }

    @Test
    fun `domain policies do not inject io ports or depend on frameworks`() {
        noClasses().that().resideInAPackage("com.3dify.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.3dify.shared.metrics..",
                "org.springframework.beans..",
                "org.springframework.boot..",
                "org.springframework.scheduling..",
                "jakarta.persistence..",
                "javax.persistence..",
            ).check(production)
        production.filter { it.packageName.startsWith("com.3dify.domain") && it.isAnnotatedWith(org.springframework.stereotype.Component::class.java) }
            .forEach { policy ->
                check(policy.constructors.all { constructor -> constructor.rawParameterTypes.none { it.isInterface } }) {
                    "${policy.name} must receive values rather than inject I/O interfaces"
                }
            }
    }

    @Test
    fun `all project classes use the renamed namespace`() {
        val all = ClassFileImporter().withImportOption(ImportOption.DoNotIncludeTests()).importPackages("com.omni3d", "com.3dify")
        check(all.none { it.packageName.startsWith("com.omni3d") })
    }

    @Test
    fun `shared types cannot bypass the layers and persistence stays in infrastructure`() {
        noClasses().that().resideInAPackage("com.3dify.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.3dify.domain..",
                "com.3dify.application..",
                "com.3dify.infrastructure..",
                "com.3dify.interfaces..",
            ).check(production)
        noClasses().that().resideOutsideOfPackage("com.3dify.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("org.jooq..", "org.springframework.jdbc..", "jakarta.persistence..")
            .check(production)
    }
}
