# DSA migration plan

## Metadata

- Date: 2026-10-04
- Scope: the complete Kotlin/Spring backend, including authentication filters, REST endpoints, Stripe and RunPod callbacks, Rabbit consumer, and outbox scheduler.
- Source architecture: classic Spring services moved into DSA-named packages without dependency inversion or use-case separation.
- Target: layer-first DSA, one application-service class per business use case, one public execution method per class.
- Status: structural DSA migration complete. Workspace/store-scoped Shopify backend is implemented and hardened; the separate merchant app is implemented in `3dify-shopify`. Live Shopify/provider validation remains pending credentials. Explicit multi-workspace/staff authorization and WooCommerce are deferred by user decision (2026-10-05).
- Historical discovery started with a clean working tree. Migration and Shopify/CI work have since been committed and pushed; no deployment has been performed. See `docs/handoffs/README.md` for current session state.

## Original DSA contract freeze (historical)

The following applied to the original architecture-only refactor. The user subsequently authorized replacement of the database baseline and ADR; the schema/Flyway freeze is superseded for that change.

Preserve HTTP routes, verbs, status codes, JSON fields and nullability, error text, authentication and authorization behavior, JWT claims, API key prefix/hash, database schema and Flyway history, Rabbit queue/exchange/routing keys and task JSON, Stripe metadata and event handling, RunPod request/callback fields, configuration keys, metrics, and the 500 ms outbox poll with batch size 50. Preserve referenced Spring bean identities when introducing adapters.

Preserve existing side-effect order, exception handling, transaction boundaries and duplicate processing behavior. New idempotency enforcement, compensation, transaction expansion, access-control fixes, and delivery guarantees require a separate behavior-change decision.

## Inventory summary and project context

Initial inventory: 47 production files, including nine broad application files. Current inventory: 153 production files, including 26 single-use-case application services. Domain models/ports and application commands/queries/results now have separate files grouped by concept. The inventory is heuristic; its multi-service-controller flags are resolved by the architecture rule that checks each handler invokes exactly one service.

The product provides image-to-3D generation through API keys, with a user dashboard, subscription billing, admin-managed pricing, asynchronous GPU generation, downloadable outputs, and optional completion webhooks.

- Registration atomically provisions a user/workspace/owner/direct billing scope; login issues JWTs. JWT authentication additionally reads administrator status from PostgreSQL.
- API key access loads the key and subscription, enforces subscription eligibility, then consumes a PostgreSQL-backed rate-limit token.
- Generation uploads two images to storage, writes a pending job/history/outbox in a database transaction, and returns the job ID.
- The outbox relay publishes task JSON to Rabbit. The worker marks processing, submits RunPod generation, and stores its external task ID; dispatch failures record a failed job.
- RunPod callbacks verify the external task/job association, update job results/history/metrics, and synchronously attempt the user's configured webhook. Delivery failures are swallowed and measured.
- Plans control price, quota and rate limit. Paid plan creation creates a Stripe product and recurring price. Plan updates currently do not update Stripe pricing.
- Checkout either activates a free subscription and updates API-key plans, or creates a Stripe subscription checkout. Billing portal requires a linked Stripe customer.
- Stripe callbacks activate subscriptions, update their status, deactivate/reactivate API keys, or mark payment overdue.

Domain groups: identity, API access, plans, subscriptions, generation jobs, customer webhook configuration, with delivery/outbox entirely in infrastructure. These are packages inside the current service, not proposed new services or databases. Cross-group orchestration stays in the owning use case.

## Class map

Target application directory: `application/service/<group>/<usecase>/`. Every listed name ends in `ApplicationService`, with its own command/query and use-case result where needed.

| Current class/entry point | Target use cases / responsibility |
|---|---|
| UserService | RegisterUser, LoginUser |
| JwtAuthFilter + JwtService + UserRepository | AuthenticateJwt; filter only extracts token and builds Spring authentication |
| ApiKeyService | CreateApiKey, ListApiKeys, RevokeApiKey |
| ApiKeyAuthFilter + validateRawKey + RateLimiterService | AuthorizeApiRequest: key validation, subscription eligibility and rate consumption in one use case; filter maps the outcome to the existing HTTP/auth behavior |
| PlanService | ListActivePlans, ListPlans, GetPlan, CreatePlan, UpdatePlan, DeactivatePlan; retain GetPlan only if callers require it |
| SubscriptionService | GetSubscriptionStatus, CreateCheckoutSession, CreateBillingPortal, HandleStripeWebhook |
| Stripe webhook private handlers | Typed domain event variants and subscription decisions used by HandleStripeWebhook; no application-service chaining. Add separate event use cases only if separately dispatched at the interface boundary |
| GenerateController + JobService.createJob | GenerateModel: input validation, two uploads, job/history/outbox persistence; preserve uploads before the DB transaction |
| JobService reads + JobHistoryService.getHistory | GetJob, ListApiKeyJobs, ListUserJobs, GetJobHistory |
| TaskWorker + JobService processing/dispatch/failure helpers | DispatchGenerationTask: complete worker operation including failure recording; listener deserializes and makes one call |
| ProviderWebhookController + JobService result helpers | HandleGenerationCallback: verify association, apply result, persist history, measure, attempt customer notification; controller maps result and preserves 400/500 behavior |
| WebhookService | GetWebhook, SetWebhook, DeleteWebhook; resolve URL becomes a repository operation used by callback orchestration |
| OutboxService | Infrastructure outbox-backed GenerationTaskPublisher implementing a domain port; JSON serialization stays in infrastructure |
| OutboxPublisher | Infrastructure OutboxPublisher scheduler and OutboxRelay; storage, delivery and serialization stay in infrastructure |
| JobHistoryService.recordChange | JobHistoryRepository domain port used within the owning use cases |
| Eight infrastructure repositories | Domain repository interfaces with current intent/signatures; existing SQL implementations become Postgres*Repository adapters |
| StorageService, RunPodClient, Stripe SDK calls | ImageStorage, GenerationProviderClient, BillingClient domain ports and infrastructure implementations |
| JwtService, PasswordEncoder, RateLimiterService | TokenClient, PasswordHasher, RequestRateLimiter domain ports; current implementations remain infrastructure details |
| domain/entities.kt | Separate pure model/projection files by owning domain group; preserve values and construction semantics |
| REST Dtos.kt | Keep API shapes in interfaces; introduce application commands/queries/results and explicit interface mappers |
| ApiException + GlobalExceptionHandler | Technology-independent application/domain failures; HTTP translation in interfaces/rest/exceptions, preserving existing status/text |
| SecurityConfig | Move HTTP security/filter wiring to interfaces config; password implementation wiring remains infrastructure; avoid infrastructure importing interfaces |
| TaskProducer.TaskMessage | Neutral task contract independent of either adapter; preserve existing wire JSON |

Domain policies/factories own existing decisions: API-key format/active checks, subscription access eligibility, free/paid checkout eligibility, subscription event effects, callback outcome selection, generation input validity, and user registration/credential decisions. Application services load values and pass them to these policies. Domain policies never inject I/O ports.

## Slices

| Slice | Status | Completion gate |
|---|---|---|
| 0. Baseline repair | Complete | Repair bootstrap mismatch and run full build/tests; record actual remaining failures |
| 1. Foundations and architecture guardrails | Complete | Domain ports, adapter wiring, transaction abstraction preserving current semantics; strict architecture rules with zero allowed violations |
| 2. Active plan read pilot | Complete | ListActivePlansApplicationService + result + controller mapper; endpoint characterization and architecture checks green |
| 3. Remaining plan reads/writes | Complete | Separate services, billing adapter, pricing policy; existing admin/public contracts pass |
| 4. Identity and API access | Complete | Registration/login/key use cases and thin JWT/API filters; preserve gate order and rate-token consumption |
| 5. Webhook configuration and job reads | Complete | Separate query/command services; response/status contracts pass |
| 6. Subscription use cases | Complete | Four separate services, Stripe adapter and typed events, pure policies; free/paid/portal/event paths characterized |
| 7. Generation submission | Complete | One generate use case, storage and outbox ports; transactional job/history/outbox behavior verified |
| 8. Dispatch, provider callback and relay | Complete | Thin listener/callback/scheduler; orchestration and adapters extracted with failure/side-effect order preserved |
| 9. Finish | Complete | No legacy multi-use-case services, no stale forwarding wrappers, zero architecture violations, full build/tests green |

Update this file after each slice with changed classes, validation results and remaining findings. Do not weaken tests or add permanent architecture exceptions to finish a slice.

## Decisions

- The user's one-class-per-business-case requirement is stronger than the skill's allowance for several methods in a class, and governs this migration.
- A mechanical split that forwards into the old broad service is insufficient. Each service must own its complete orchestration and depend only on domain ports/policies and approved cross-cutting concerns.
- Existing API DTOs must not be imported by application services. Domain objects and vendor SDK objects must not become endpoint response contracts.
- No generic repository/state-machine framework is proposed yet: the current SQL and lifecycle behavior do not justify it without a concrete recurring need.
- User authorized namespace correction on 2026-10-04: all packages, test directories, build group, generated-code target and logger namespaces become `com.thridify` (ordinary Kotlin identifiers; the earlier numeric namespace was superseded). External configuration/database/broker/metric identifiers remain frozen.

## Findings

1. Resolved: initial baseline was red: `./gradlew build` fails at compileKotlin because `3difyApplication.kt:12` calls `runApplication<Omni3dApplication>` while the declared class is backtick-escaped `3difyApplication`. Tests did not run. Observed Gradle reported build duration: 1 s.
2. Resolved: eight strict ArchUnit checks now enforce layers, technology restrictions, use-case ownership, entry-point composition, domain policies, namespace, shared dependencies and persistence placement. No frozen store, ignores, or violation budget remains.
3. Resolved: application services now depend on domain ports/policies and cross-cutting metrics/logging only. Broad services and application-service chaining were removed.
4. Resolved: REST endpoints, authentication filters, callbacks and listener each invoke one application service; the technical outbox scheduler stays in infrastructure. Infrastructure implements all SQL/vendor/storage/crypto/rate-limit/notification ports.
5. Stripe checkout mutates an external service while its DB transaction is open. Changing timing/compensation is a separate behavior change.
6. Worker and provider callbacks perform multiple independent database writes without a single transaction. Adding atomicity would change existing partial-failure behavior.
7. Command and callback deduplication is absent. Outbox/Rabbit redelivery and repeated Stripe/RunPod callbacks can repeat side effects. Introducing idempotency needs a separate decision and possibly persistent schema support.
8. API job retrieval/history endpoints do not check ownership in their current service calls. Any access-control correction is separate from the architecture refactor.
9. Worker failure details contain a literal escaped interpolation (`GPU Provider Error: ${ex.message}`); preserve until a bug fix is separately approved.
10. Verified: Testcontainers PostgreSQL, mocked Rabbit/S3/RunPod, Spring wiring and transactional rollback all pass in the full build. RunPod remains a simulation in the current implementation.

## Follow-ups

- Idempotency, transaction/compensation improvements and access-control fixes are behavior changes and remain separately scoped. The migration does not claim these existing operational gaps are solved.
- The replacement [workspace/billing ADR](../adr/0001-workspaces-and-store-scoped-billing.md) governs the fresh V1 schema. Persistence support for default direct workspaces is implemented; explicit workspace authorization and Shopify/WooCommerce runtime integrations are not.
- Development database now defaults to `thridify`; legacy broker/config/metric identifiers retain their existing identities. Package declarations, imports, test paths, Gradle group, codegen package and logger namespace are `com.thridify`.

## Execution log

- Baseline: corrected the bootstrap generic to the declared `3difyApplication`; `./gradlew build` passed in 12 s.
- Foundations: eight domain repository ports and Postgres adapters preserving SQL and bean names; technology-independent failures; Spring transaction provider preserving default unchecked-exception rollback semantics.
- Pilot: ListActivePlansApplicationService with separate application result and REST mapping; PublicPlanControllerTest passed (8 s).
- Plans: all admin use cases separated; AdminControllerTest and PublicPlanControllerTest passed (7 s).
- Identity/access: register, login, key creation/list/revocation, token authentication and complete API authorization separated; password/token/rate-limiter ports; existing unit and endpoint tests passed (8 s).
- Subscriptions: status, checkout, portal and webhook services, domain policy, typed billing events and Stripe adapter; existing unit and endpoint tests passed (6 s).
- Namespace correction: user explicitly requested `com.thridify`; source/test trees moved to ordinary `com.thridify` packages, build group/codegen/logging updated. Subscription/authentication checks passed after rename (9 s). External database/config/broker/metric identities preserved.
- Generation: submission, dispatch, callback, relay and queries implemented; application services compose domain ports only; former broad JobService, JobHistoryService and OutboxService removed.
- Webhooks: get/set/delete services implemented; former broad WebhookService removed.
- Guardrails: ArchUnit 1.3 could not import JVM 26 bytecode. Upgraded to locally available 1.5.0 and added an explicit import sanity check. Temporary frozen artifacts from the incompatible import were discarded; final rules are strict and have no violation allowances.

- Added regression coverage for job/history/outbox rollback, checked-exception transaction semantics, upload failures, subscription/rate-limit gate order, dispatch failure details, relay continuation, paid checkout and portal, signed Stripe event mapping, subscription event effects, generation message contracts, and callback notification failures.
- Manually compared all eight persistence implementations against their original class bodies: SQL and record-mapping behavior are unchanged. Flyway migrations were not edited.
- Bootstrap JAR verified: Start-Class and packaged application classes use `com.thridify`; no `com.omni3d` production classes are packaged.
- Documentation updated: README, architecture overview, system flows/endpoint map, migration findings and final inventory. The inventory generator was run from a temporary copy with backtick-aware package parsing; installed skills were not modified.

- Original DSA validation: `./gradlew build` passed; 93 tests across 20 suites, zero failures/errors/skips. Includes seven strict architecture tests and PostgreSQL integration tests.

- Project renamed to `thridify` at user request; namespace is `com.thridify` with ordinary Kotlin identifiers. Package-name lint enforcement restored. Existing local database and broker identities are preserved.

- Outbox encapsulation: moved outbox models, repository, delivery contract/adapters, relay and scheduler into `infrastructure/outbox`. Application submission only calls `GenerationTaskPublisher.publish`; relay is a technical process, not an application use case. Kept the 500 ms delay, batch size 50, transactional insertion, failed-message continuation and metrics. Added a strict architecture guard for outbox isolation; full build passed with 94 tests.

## Fresh workspace/store-billing baseline (2026-10-04)

- User authorized replacing the previous ADR and all migration scripts because no deployment exists. Retired V1–V12; added a fresh V1 with 15 tables and workspace/scope integrity constraints. Historical claims above about preserved SQL/migrations describe the initial DSA phase, not this subsequent schema change.
- Replaced ADR 0001 with workspace ownership, per-store subscriptions/allowances, provider-neutral billing references and separated plan offers. Shared quota is deferred.
- Direct signup provisions user/workspace/membership/scope in one SQL statement. Persistence adapters map existing direct-user APIs through default workspace/direct scope, move provider prices to offers, and derive job tenant/scope from API keys. Some domain methods/DTOs still use userId/Stripe vocabulary; explicit context and generic billing use cases remain future work.
- Integration fixtures now target the new schema. Added database regression tests for independent store billing/allowance, cross-workspace and cross-store references, current-subscription uniqueness/history, provider namespaces, quota bounds and atomic signup provisioning.
- Created a fresh local `thridify` database; the older `3dify` database was preserved. Updated Compose, application and Gradle defaults, and regenerated jOOQ from the fresh baseline.
- Updated README, architecture overview, system flows, schema/setup guide and inventory.

- Fresh-baseline validation: Flyway migration and jOOQ generation succeeded against local `thridify`; full build passed with 102 tests, including eight architecture checks and PostgreSQL schema-isolation regressions.

## Shopify hardening and separate app (2026-10-05)

The user confirmed remote CI works and scoped remaining work to Shopify/backend hardening; broader workspaces/staff/API-key authorization and WooCommerce are deferred. Annual Shopify allowance resets monthly, anchored to the provider cycle start. Concurrent retry/last-quota behavior is serialized and covered by a two-thread database integration regression. Billing history explicitly searches older windows so freezes do not disappear at the historical API default window. Malformed upstream responses and GraphQL permissions/throttling are mapped intentionally. Generated outputs join retryable privacy deletion. RunPod simulation is removed in favor of disabled-by-default real async dispatch, with an explicit worker/output-storage contract. Callback terminal failures and incomplete outputs fail the job.

The earlier execution log is historical: direct Stripe/general API behavior and their pre-existing idempotency/transaction findings are not all changed by this Shopify-scoped task. Live verification requires credentials and a provisioned inference worker. See `docs/shopify/LAUNCH.md` and current session handoff.
