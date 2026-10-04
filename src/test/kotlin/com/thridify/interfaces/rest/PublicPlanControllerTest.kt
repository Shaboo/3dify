package com.thridify.interfaces.rest

import com.thridify.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class PublicPlanControllerTest : IntegrationTestBase() {

    @BeforeEach
    fun setup() {
        // No need to resetDatabase for plan tests — migrations seed the plans table.
        // But reset to ensure clean state if other tests touched it.
        resetDatabase()
    }

    @Test
    fun `GET public plans returns active plans without auth`() {
        // After reset, plans table is empty (truncated). We need to re-seed.
        // The seed data comes from Flyway migrations V8 — they are only run once on startup.
        // So after truncation we need to re-insert manually via DSL.
        seedPlans()

        mockMvc.get("/public/plans")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) } // free, pro, enterprise (from seed)
                jsonPath("$[0].name") { exists() }
                jsonPath("$[0].displayName") { exists() }
                jsonPath("$[0].priceCents") { exists() }
            }
    }

    @Test
    fun `GET public plans returns only active plans`() {
        seedPlans()

        // Deactivate all (simulate all deactivated)
        dsl.execute("UPDATE plans SET is_active = false")

        mockMvc.get("/public/plans")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `GET public plans returns 200 even when no plans exist`() {
        // DB was reset (no plans), should return empty array
        mockMvc.get("/public/plans")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    private fun seedPlans() {
        dsl.execute(
            """
            INSERT INTO plans (id, name, display_name, description, rate_limit_rpm, monthly_quota, price_cents, currency, is_active, sort_order)
            VALUES
                (gen_random_uuid(), 'free',       'Free',       'Free tier',        60,  100,  0,     'usd', true, 1),
                (gen_random_uuid(), 'pro',        'Pro',        'Pro tier',         300, 1000, 2900,  'usd', true, 2),
                (gen_random_uuid(), 'enterprise', 'Enterprise', 'Enterprise tier',  600, 5000, 9900,  'usd', true, 3)
            """.trimIndent(),
        )
    }
}
