# thridify

Kotlin/Spring backend for image-to-3D generation, API-key access, dashboard authentication, Stripe subscriptions, and asynchronous GPU jobs.

The code namespace is `com.thridify`. The backend follows DSA with one application service per use case, pure domain policies and ports, infrastructure adapters, and thin protocol entry points.

- [Architecture](architecture.md)
- [System flows and endpoint map](system_flow.md)
- [DSA migration plan and findings](docs/dsa-migration/PLAN.md)
- [Workspace and store-scoped billing decision](docs/adr/0001-workspaces-and-store-scoped-billing.md)

- [Database baseline and local setup](docs/database.md)

The fresh V1 schema supports workspace ownership and independent store billing scopes. Existing direct-user/Stripe flows work through a default workspace; Shopify backend installation, billing synchronization, scoped model APIs and quota enforcement are implemented. The merchant frontend and product attachment belong in a separate Shopify app. Shopify is disabled by default pending real credentials and store validation.

Build and run tests with `./gradlew build`. The build uses JDK 27 and targets JVM 26. Integration tests require Docker for PostgreSQL Testcontainers and mock external infrastructure.

Development commands are available through `make`:

| Command | Action |
|---|---|
| `make help` | List every target |
| `make format` | Run `./gradlew spotlessApply` using ktlint |
| `make lint` | Check formatting without changing files |
| `make build` | Build, check formatting, and run tests |
| `make test` | Run all tests |
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

Local runtime settings are in `src/main/resources/application.yml`; infrastructure definitions are in `docker-compose.yml` and `rabbitmq/`. The RunPod adapter currently simulates generation callbacks.
