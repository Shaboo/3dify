# thridify

Kotlin/Spring backend for image-to-3D generation, API-key access, dashboard authentication, Stripe subscriptions, and asynchronous 3D generation jobs.

The code namespace is `com.thridify`. The backend follows DSA with one application service per use case, pure domain policies and ports, infrastructure adapters, and thin protocol entry points.

- [Latest session handoff](docs/handoffs/README.md)
- [Generation providers and Meshy setup](docs/generation/PROVIDERS.md)
- [Architecture](architecture.md)
- [System flows and endpoint map](system_flow.md)
- [DSA migration plan and findings](docs/dsa-migration/PLAN.md)
- [Workspace and store-scoped billing decision](docs/adr/0001-workspaces-and-store-scoped-billing.md)

- [Database baseline and local setup](docs/database.md)

The fresh V1 schema supports workspace ownership and independent store billing scopes. Existing direct-user/Stripe flows work through a default workspace; Shopify backend installation, billing synchronization, scoped model APIs and quota enforcement are implemented. The merchant frontend and product attachment live in [3dify-shopify](https://github.com/Shaboo/3dify-shopify). Shopify is disabled by default pending real credentials and store validation.

Build and run tests with `./gradlew build`. The build uses JDK 27 and targets JVM 26. All integration tests extend `IntegrationTestBase` and use a fresh PostgreSQL 16 Testcontainer with Flyway migrations. Docker must be running; no Compose services or local database are needed. The container lives for one test JVM and Testcontainers cleans it up afterward. Database tests run sequentially and reset application tables between cases. RabbitMQ, S3 and remote provider adapters are mocked. A test architecture check enforces the shared setup for Spring integration tests.

Development commands are available through `make`:

| Command | Action |
|---|---|
| `make help` | List every target |
| `make format` | Run `./gradlew spotlessApply` using ktlint |
| `make lint` | Check formatting without changing files |
| `make build` | Build, check formatting, and run tests |
| `make test` | Run all tests |
| `make unit-test` | Run unit and architecture tests |
| `make integration-test` | Run only Testcontainers integration tests |
| `make check` | Run formatting checks and tests |
| `make docker` | Start PostgreSQL, RabbitMQ, and Redis; wait for readiness |
| `make docker-down` | Stop the local services, preserving volumes |
| `make docker-logs` | Follow service logs |
| `make docker-status` | Show service status |
| `make migrate` | Apply Flyway migrations |
| `make migrate-info` | Show migration status |
| `make jooq` | Apply migrations, then generate jOOQ Kotlin sources |
| `make run` | Run the application |
| `make clean` | Remove build output |

For a fresh local database, run `make docker`, then `make jooq`. If your Compose volume contains the earlier database, first run `docker compose exec -T postgres createdb -U 3dify thridify`; this creates the new baseline database and preserves the old one. See the [database guide](docs/database.md). Generated sources live in `build/generated-src/jooq/main` and are excluded from formatting. Builds and tests can run without generating them while the persistence adapters use their existing SQL.

Gradle migrations, jOOQ generation, and the application use the Compose database `thridify` with local user/password `3dify`. Override these with `DB_URL`, `DB_USER`, and `DB_PASSWORD`, for example `DB_URL=jdbc:postgresql://localhost:5433/thridify make jooq`.

Spotless checks run as part of `check` and `build`. All ktlint rules, including package naming, are enforced.

Local runtime settings are in `src/main/resources/application.yml`; infrastructure definitions are in `docker-compose.yml` and `rabbitmq/`. Meshy is the default generation adapter; enable its credentials and generation polling explicitly. Two-image jobs use Multi-Image-to-3D and retain both output formats in owned storage. RunPod remains an interchangeable alternative for a future owned worker. See the [Shopify launch runbook](docs/shopify/LAUNCH.md).

GitHub Actions CI runs on pushes to `main`, pull requests and manual dispatch. Two independent jobs run in parallel: one checks formatting with `spotlessCheck`, builds the application JAR and runs `unitTest` (including architecture checks); the other runs `integrationTest` using Docker/Testcontainers. The suites use complementary JUnit tags, so tests are not duplicated. Both jobs publish test reports, including on failure; the build job also publishes the application JAR. Local `make test` and `make build` continue to run the complete suite.
