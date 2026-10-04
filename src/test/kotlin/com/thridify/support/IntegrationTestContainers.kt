package com.thridify.support

import org.testcontainers.containers.PostgreSQLContainer

/** JVM-scoped lifecycle keeps containers alive across cached Spring contexts. Ryuk handles cleanup. */
object IntegrationTestContainers {
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("thridify_test")
        .withUsername("thridify")
        .withPassword("thridify")
        .withReuse(false)
        .also { it.start() }
}
