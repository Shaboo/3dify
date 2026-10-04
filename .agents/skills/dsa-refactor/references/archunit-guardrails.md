# ArchUnit guardrails for a DSA migration

Install these **before** moving code (Phase 3). They turn "is this DSA yet?" into a number that can only
go down. Kotlin + JUnit 5 shown; Java is analogous.

## Contents
1. Dependency
2. Ratchet strategy: freeze (recommended) or count budget
3. Rule set (copy and adapt)
4. Pitfalls that silently disable rules
5. Finishing

---

## 1. Dependency

```kotlin
// build.gradle.kts (use the version catalog if the repo has one)
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```
If the repo already has ArchUnit tests, **extend them** instead of adding a parallel suite. Keep their
layer identifiers and helpers.

## 2. Ratchet strategy

**Recommended: `FreezingArchRule`.** It records today's exact violations in a text store. New violations
fail the build, fixed ones are removed from the store automatically, and you can't trade one violation
for another (unlike a plain count).

`src/test/resources/archunit.properties`:
```properties
freeze.store.default.path=src/test/resources/archunit_store
# true only for the very first run that creates the store; then commit the store and set it to false,
# otherwise a CI checkout without the store would silently re-baseline.
freeze.store.default.allowStoreCreation=true
```
Commit `archunit_store/` with the rules. After each migration slice, the store shrinks. Commit that diff:
it is your progress meter.

**Alternative: count budget** (what domain-service-template and sepa-processor use):
`assertThatEvaluationResultIsValid(rule.evaluate(classes), allowedNumberOfViolations = N)`. Get N by running
with 0 and reading `actual: N` from the failure. Lower N in every slice and never raise it. It's simpler
but weaker: a slice can fix one violation and add another without anyone noticing.

## 3. Rule set

```kotlin
package com.traderepublic.banking.<name>.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.freeze.FreezingArchRule.freeze
import org.junit.jupiter.api.Test
import org.springframework.stereotype.Service

private const val ROOT = "com.traderepublic.banking.<name>"

// Flat layout: anchor at ROOT so a nested package that happens to be called "domain" or "shared" doesn't get
// swept into the wrong layer. Per-model layout (<model>/{domain,...}): use "..domain.." etc. instead.
private const val INTERFACES = "$ROOT.interfaces.."
private const val APPLICATION = "$ROOT.application.service.."   // not "..application..": app-level config classes live in application/
private const val DOMAIN = "$ROOT.domain.."
private const val INFRASTRUCTURE = "$ROOT.infrastructure.."

private val production: JavaClasses by lazy {
    ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .withImportOption(ImportOption { location -> !location.contains("/generated/") })
        .importPackages(ROOT)
}

class DsaArchitectureTest {
    @Test
    fun `layers depend inward only`() =
        freeze(
            layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Interfaces").definedBy(INTERFACES)
                .layer("Application").definedBy(APPLICATION)
                .layer("Domain").definedBy(DOMAIN)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .whereLayer("Domain").mayNotAccessAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .withOptionalLayers(true),
        ).check(production)

    @Test
    fun `application services never depend on other application services`() =
        freeze(
            classes().that().haveSimpleNameEndingWith("ApplicationService")
                .should(notDependOnOtherApplicationServices),
        ).check(production)

    @Test
    fun `domain and application stay free of technology`() =
        freeze(
            noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.jooq..", "jakarta.persistence..", "javax.persistence..", "org.springframework.data..",
                    "org.springframework.jdbc..", "org.springframework.transaction..",
                    "org.springframework.amqp..", "org.springframework.kafka..", "org.apache.kafka..",
                    "org.springframework.web..", "org.springframework.http..", "software.amazon.awssdk..",
                    "com.fasterxml.jackson..", "jakarta.xml.bind..",
                )
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value")
                .orShould().dependOnClassesThat().resideInAPackage("org.springframework.boot.context.properties.."),
        ).check(production)

    @Test
    fun `persistence technology only in infrastructure`() =
        freeze(
            noClasses().that().resideOutsideOfPackage(INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.jooq..", "jakarta.persistence..", "javax.persistence..", "org.springframework.jdbc..",
                ),
        ).check(production)

    @Test
    fun `application-layer services are named ApplicationService`() =
        classes().that().resideInAPackage(APPLICATION).and().areAnnotatedWith(Service::class.java)
            .should().haveSimpleNameEndingWith("ApplicationService")
            .allowEmptyShould(true)
            .check(production)

    private val notDependOnOtherApplicationServices =
        object : ArchCondition<JavaClass>("not depend on other application services") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                item.directDependenciesFromSelf
                    .filter { dependency ->
                        val target = dependency.targetClass
                        target.simpleName.endsWith("ApplicationService") &&
                            target.name != item.name &&
                            !target.name.startsWith(item.name + "$")
                    }.forEach { dependency -> events.add(SimpleConditionEvent.violated(dependency, dependency.description)) }
            }
        }
}
```

Add repo-specific rules in the same file as the migration progresses:
- naming (`*Dao` extends the DAO base class, listeners end in `*Listener`);
- test rules (MockK only, no Mockito; JUnit 5 only).

During the migration, old-layout code that sits in none of the four layer packages is invisible to the
layer rule (`consideringOnlyDependenciesInLayers`). That's intended: the rules tighten automatically as
classes move into layer packages. Track "classes still outside any layer" with the inventory script.

## 4. Pitfalls that silently disable rules

- **Package identifiers need `..` to include sub-packages.** `resideOutsideOfPackages("com.acme.app")`
  matches only that exact package, so *every* class in a sub-package counts as "outside". Used inside
  `ignoreDependency(...)`, that turns the whole rule into a no-op. sepa-processor's four "may only access"
  rules have exactly this bug. Write `"com.acme.app.."`.
- **Wildcard layers over-match.** `"..domain.."` also matches `infrastructure.outbox.event.domain`, and `"..shared.."` matches
  `interfaces.rest.shared`. Anchor at the root package for flat layouts.
- **ArchUnit can't see some dependencies:**
  - inlined Kotlin `const val`s and Java `static final` primitives/strings;
  - types only referenced in `catch` clauses;
  - reflection and Spring bean names.
  Grep for these in review.
- `consideringAllDependencies()` makes layer rules also judge JDK/library targets. Prefer
  `consideringOnlyDependenciesInLayers()` for layer rules and separate rules for technology restrictions.
- `withOptionalLayers(true)` / `allowEmptyShould(true)` keep rules green while a layer is still empty.
  A typo in a package identifier then *also* stays green. Sanity-check each rule once by introducing a
  deliberate violation locally.
- Escape-hatch annotations (`ignoreDependency(annotatedWith(...))`) are migration debt. Count them in the plan
  and remove them by the end.
- Generated code (jOOQ, xjc, MapStruct `*Impl`) needs a decision: exclude it via an import option, or assign it to
  the layer of its source.

## 5. Finishing

When the store for a rule is empty (or its budget is 0):
1. Set `allowStoreCreation=false` and delete the empty store file. Optionally replace `freeze(rule)` with `rule` for the rules that are clean.
2. Remove temporary ignores and escape hatches, and tighten layer identifiers to their final form.
3. Add the stricter rules you postponed (naming, visibility, test-library rules).
