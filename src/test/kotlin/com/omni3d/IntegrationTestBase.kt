package com.omni3d

import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestBeanConfig::class)
abstract class IntegrationTestBase {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var dsl: DSLContext

    /** Truncate all application tables between tests for isolation. */
    fun resetDatabase() {
        dsl.execute("""
            TRUNCATE TABLE
                subscriptions,
                api_keys,
                job_history,
                jobs,
                outbox_messages,
                webhooks,
                users,
                plans
            RESTART IDENTITY CASCADE
        """.trimIndent())
    }

    /**
     * Re-inserts the default plans (free + pro) after a resetDatabase() call.
     * Mirrors what Flyway migrations would seed in production.
     */
    fun seedDefaultPlans() {
        dsl.execute("""
            INSERT INTO plans (id, name, display_name, rate_limit_rpm, monthly_quota, price_cents, currency, is_active, sort_order)
            VALUES
                (gen_random_uuid(), 'free',  'Free',  60,  50,   0,    'usd', true, 1),
                (gen_random_uuid(), 'pro',   'Pro',   300, 500,  2900, 'usd', true, 2)
            ON CONFLICT DO NOTHING
        """.trimIndent())
    }

    companion object {
        // Single JVM-level Postgres container shared across ALL test classes.
        // Using a Kotlin object avoids the container being stopped when the first
        // Spring context is torn down between test class runs.
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("omni3d_test")
                .withUsername("omni3d")
                .withPassword("omni3d")
                .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            // Disable RabbitMQ for integration tests
            registry.add("spring.rabbitmq.host") { "localhost" }
            // Stub Stripe keys
            registry.add("stripe.secret-key") { "sk_test_stub" }
            registry.add("stripe.webhook-secret") { "whsec_stub" }
            // Stub RunPod properties
            registry.add("omni3d.runpod.api-url") { "http://stub" }
            registry.add("omni3d.runpod.api-key") { "stub_key" }
            registry.add("omni3d.runpod.webhook-url") { "http://stub/webhook" }
        }
    }
}
