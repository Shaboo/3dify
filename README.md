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

### Run from IntelliJ

Select the shared **3dify Local** run configuration and click Run. It starts Compose services before launching the backend on port 8080. Docker Desktop must be running, and the project SDK must be JDK 27. Reload the Gradle project if the configuration or new task is not visible. The configuration uses the existing IntelliJ module `3dify.main`; if importing under a different project name, select that project's main module in Edit Configurations.

Local settings are loaded from `config/application-local.properties`, without shell exports or a dotenv plugin. This file is ignored by Git; a tracked template lives at `config/application-local.properties.example`. On a fresh checkout, copy the template to `config/application-local.properties` once. Fill in `shopify.client-secret` and `shopify.frontend-origins` to test store connection. The public test app client ID is prefilled. Set pricing/Partner credentials and storage/provider settings for the corresponding live workflows. Enable both Meshy and generation polling when testing Meshy generation.

Keep your backend HTTPS tunnel running separately, set its URL as `VITE_BACKEND_URL` in the frontend, and run `shopify app dev`. Update `shopify.frontend-origins` when the frontend tunnel changes. The frontend origin and backend tunnel URL are separate settings. Restart the backend after editing local properties. Secrets stay in the ignored local file; never add them to the example or shared run configuration.

For generation testing before Shopify billing is available, set `shopify.billing-mode=local-test` in the ignored local properties, together with `shopify.local-test-shop-id` (verified `gid://shopify/Shop/...`), `shopify.local-test-shop-domain` (`...myshopify.com`) and `shopify.local-test-generation-limit=10`. This mode starts only when `local` is the sole active Spring profile; **3dify Local** activates it explicitly. Other stores receive no test allowance. Partner billing is skipped, while Shopify authentication, product permissions, generation, quota accounting and automatic attachment remain real. Meshy calls can cost money. The allowance persists across retries, refreshes and restarts and renews at UTC calendar-month boundaries. The internal inactive test plan has no purchasable offer mapping. Both app surfaces label local testing, and Manage plan is disabled. To restore real billing, set `shopify.billing-mode=shopify` and restart; successful Shopify billing confirmation is required before new generation.

GitHub Actions CI runs on pushes to `main`, pull requests and manual dispatch. Two independent jobs run in parallel: one checks formatting with `spotlessCheck`, builds the application JAR and runs `unitTest` (including architecture checks); the other runs `integrationTest` using Docker/Testcontainers. The suites use complementary JUnit tags, so tests are not duplicated. Both jobs publish test reports, including on failure; the build job also publishes the application JAR. Local `make test` and `make build` continue to run the complete suite.

### Generation photos

Both `POST /api/v1/generate` and `POST /shopify/api/models` accept repeated multipart `images` parts, in view order. The backend persists and queues the complete list without a fixed photo-count ceiling, including provider retries and Shopify privacy cleanup. The legacy `image1` + `image2` pair remains supported; do not mix it with `images`.

The selected provider validates its own count before uploads or allowance consumption: Meshy accepts 1–4 photos; the current RunPod worker accepts 1–2. A future provider can expose `maxInputImages = null` and implement list submission. Invalid counts return HTTP 400, without silently dropping views. Shopify reads these capabilities from the authenticated `GET /shopify/api/generation-options` endpoint.

Byte limits remain independent of photo count: 20 MB per upload and 85 MB for the complete multipart request by default. Configure `spring.servlet.multipart.max-request-size` in your local properties (or `GENERATION_MAX_REQUEST_SIZE`) for a provider requiring larger requests. Restart the backend to apply the input-list database migration automatically.

### Shopify product generation and automatic attachment

Open the embedded app and choose a saved product, or use the **Generate 3D model** admin block on its product page. Select existing product photos (front view first), or upload alternative photos in the embedded app. Product photo IDs are resolved against that product in the authenticated store; arbitrary image URLs are not accepted. The merchant must have product update access. The existing `read_products,write_products` scopes cover this flow.

The job and target product are committed together. The backend exchanges the merchant's short-lived ID token for an expiring offline Admin API token and stores access/refresh tokens encrypted with AES-256-GCM, bound to the store installation. OAuth exchange and refresh are serialized across backend instances. Store `shopify.credential-encryption-key` securely as a stable base64-encoded 32-byte key; changing it makes existing credentials unreadable. A local development key has been generated in the ignored local properties file. Production requires its own configured key. No manual store-token copy/paste is required.

With `shopify.enabled=true` and `shopify.jobs-enabled=true`, the attachment worker runs every 15 seconds. Once generation succeeds, it reads the retained GLB from owned storage, creates a Shopify staged upload, adds `MODEL_3D` media to the product, and polls until Shopify reports `READY`. The merchant can close the app. Model responses include `productId`, `attachmentStatus`, and `attachmentError`. The workflow logs and `omni3d.workflow.items{workflow="shopify_attachment"}` metric report progress and failures.

Safe failures retry without generating another model or consuming another generation allowance. Once a product mutation may have been submitted, the worker checks for the unique job marker instead of blindly creating media again. If the result cannot be confirmed after repeated checks, the attachment is marked failed for review in Shopify. Shopify processing failures and deleted products also surface separately from generation failures. Uninstall deletes stored offline credentials and cancels pending attachments; privacy redaction deletes the attachment rows with their jobs.

API additions: authenticated `GET /shopify/api/products/{numericProductId}/images`, JSON `POST /shopify/api/products/{numericProductId}/models` with `{ "imageIds": ["gid://shopify/MediaImage/..."] }` and an `Idempotency-Key` UUID, or existing multipart `POST /shopify/api/models` with `productId=gid://shopify/Product/...` plus `images` parts. Legacy unbound jobs retain the old manual attachment action.

Restart the backend to apply migrations. Real end-to-end generation still requires configured Meshy credentials, owned model storage, generation polling, and a working Shopify billing subscription/offer mapping. Automated tests use mocked Shopify/GPU boundaries and Testcontainers PostgreSQL; no paid generation or live product mutation is performed during verification.
