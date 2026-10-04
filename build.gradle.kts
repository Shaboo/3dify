import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.4.0")
    }
}

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    id("org.flywaydb.flyway") version "12.4.0"
    id("org.jooq.jooq-codegen-gradle") version "3.21.7"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "com.thridify"
version = "0.0.1-SNAPSHOT"

val localDatabaseUrl = providers.environmentVariable("DB_URL").getOrElse("jdbc:postgresql://localhost:5432/thridify")
val localDatabaseUser = providers.environmentVariable("DB_USER").getOrElse("3dify")
val localDatabasePassword = providers.environmentVariable("DB_PASSWORD").getOrElse("3dify")

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/generated/**")
        ktlint("1.8.0")
            .setEditorConfigPath("$projectDir/.editorconfig")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_26)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<JavaCompile> {
    options.release.set(26)
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Spring Boot Starters ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.bucket4j:bucket4j-postgresql:8.10.1")

    // --- Kotlin ---
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // --- Database ---
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // --- jOOQ ---
    implementation("org.jooq:jooq")
    jooqCodegen("org.postgresql:postgresql")

    // --- JWT (JJWT) ---
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // --- S3 / R2 ---
    implementation("software.amazon.awssdk:s3:2.30.11")

    // --- Stripe ---
    implementation("com.stripe:stripe-java:26.3.0")

    // --- Test ---
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// --- Flyway Config (for Gradle plugin usage) ---
flyway {
    url = localDatabaseUrl
    user = localDatabaseUser
    password = localDatabasePassword
    schemas = arrayOf("public")
}

// --- jOOQ Codegen ---
jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = localDatabaseUrl
            user = localDatabaseUser
            password = localDatabasePassword
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
            }
            generate {
                isDeprecated = false
                isRecords = true
                isImmutablePojos = true
                isFluentSetters = true
                isKotlinNotNullPojoAttributes = true
                isKotlinNotNullRecordAttributes = true
            }
            target {
                packageName = "com.thridify.infrastructure.persistence.generated"
                directory = "build/generated-src/jooq/main"
            }
        }
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir("build/generated-src/jooq/main")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Keep explicit code generation ordered without making normal builds require a live database.
tasks.named("compileKotlin") {
    mustRunAfter("jooqCodegen")
}
tasks.named("jooqCodegen") {
    mustRunAfter("flywayMigrate")
}
