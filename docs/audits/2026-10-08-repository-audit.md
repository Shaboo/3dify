What this repo does: This Kotlin/Spring backend accepts photos, runs paid 3D generation through Meshy or RunPod, and stores model outputs. It provides dashboard login, API keys, Stripe subscriptions, and store-scoped Shopify generation, billing, attachment, and privacy cleanup. The audit assumes several customers and stores using concurrent requests, persistent job histories, and one or more backend instances; it does not assume extreme traffic.

Audit date: 2026-10-08. The findings below are the original audit snapshot; the resolution log tracks subsequent fixes. Application code was not changed. Findings below come from traced source paths and existing tests; security exploits and paid provider calls were not run.

## Must fix

1. **A customer can read another customer's job and history** (`src/main/kotlin/com/thridify/interfaces/rest/GenerateController.kt:45`, `src/main/kotlin/com/thridify/interfaces/rest/JobController.kt:22`).
   - **What this is:** The API returns model URLs and input keys, and the API/dashboard return job history.
   - **Problem:** These routes pass only the job UUID to the shared query services, which read without ownership checks. Customer B with a valid API key or dashboard token can read customer A's known job UUID; UUID randomness is not authorization.
   - **Fix:** Pass the authenticated caller into the queries and constrain the repository reads to the caller's permitted billing scope or API key. Keep the check in the shared use case and add cross-customer tests for all three routes.
   - **If we skip it:** Leaked job identifiers expose other customers' inputs, results, and history.

2. **Customer webhooks can make the backend call private services** (`src/main/kotlin/com/thridify/application/service/webhook/set/SetWebhookApplicationService.kt:10`, `src/main/kotlin/com/thridify/infrastructure/webhook/HttpCustomerWebhookClient.kt:12`).
   - **What this is:** Users register a URL that receives a POST when generation finishes.
   - **Problem:** The URL is stored without validation and passed directly to an HTTP client. A registered URL such as `http://127.0.0.1:8080/...` makes the server send a request into its own network; this is server-side request forgery.
   - **Fix:** Validate public HTTPS destinations through a domain policy, enforce public destination addresses at connection time in the adapter or outbound proxy, and refuse redirects to private destinations. Add explicit connection/read timeouts and tests for loopback, private networks, and DNS changes.
   - **If we skip it:** An authenticated customer can reach internal HTTP services, and slow destinations can hold completion workers open.

3. **The default JWT signing key is public** (`src/main/resources/application.yml:71`, `src/main/kotlin/com/thridify/infrastructure/security/JwtService.kt:21`).
   - **What this is:** JWTs are signed login tokens that grant dashboard access.
   - **Problem:** A deployment that does not override the checked-in placeholder still starts and signs/verifies tokens with that known key. An attacker can mint a token for a known user UUID; admin access additionally requires the UUID of an actual admin because the role is read from the database.
   - **Fix:** Require an externally configured signing secret and reject the placeholder at startup outside explicit local/test configuration. Add a startup rejection test; rotate the key if a reachable deployment used the default.
   - **If we skip it:** A missed deployment setting becomes an authentication bypass. This audit did not inspect production secrets or establish that any deployed instance uses the default.

4. **The real outbox relay drops extra photos and duplicates a single photo** (`src/main/kotlin/com/thridify/infrastructure/outbox/RabbitGenerationTaskDelivery.kt:12`).
   - **What this is:** The outbox is a database queue committed with the job; its relay forwards jobs to RabbitMQ.
   - **Problem:** The relay always calls the two-image producer overload and discards `task.imageKeys`. For `[a,b,c,d]`, the worker receives `[a,b]`; for `[a]`, the fallback legacy fields produce `[a,a]`, changing Meshy's endpoint from single-image to multi-image.
   - **Fix:** Forward `task.imageKeys ?: listOf(task.inputImage1Key, task.inputImage2Key)` through the list overload. Test one, three, and four photos through the actual `RabbitGenerationTaskDelivery`; `GenerationTaskContractTest` currently tests publisher, producer, and worker separately and bypasses this broken link.
   - **If we skip it:** Accepted photos are silently lost or duplicated before a paid generation call.

5. **Stripe updates reactivate deliberately revoked keys** (`src/main/kotlin/com/thridify/application/service/subscription/webhook/HandleStripeWebhookApplicationService.kt:39`, `src/main/kotlin/com/thridify/infrastructure/persistence/PostgresApiKeyRepository.kt:107`).
   - **What this is:** Revoking an API key should permanently stop that credential from working.
   - **Problem:** Any subscription-updated event with status `active` sets every key in the scope active, including keys revoked by the user. Authorization checks `is_active` and does not check `revoked_at`, so the revoked key works again.
   - **Fix:** Treat revocation as permanent and subscription access as a separate gate; remove bulk key activation/deactivation from subscription events and rely on subscription authorization. Test revoke followed by an active subscription update.
   - **If we skip it:** Credentials revoked after a leak can become valid again without the owner's action.

6. **Free subscribers can create keys with enterprise rate limits** (`src/main/kotlin/com/thridify/application/service/apikey/create/CreateApiKeyApplicationService.kt:20`, `src/main/kotlin/com/thridify/infrastructure/persistence/PostgresApiKeyRepository.kt:85`).
   - **What this is:** Key creation accepts a caller-provided `planName` and the key's plan supplies its request rate.
   - **Problem:** A user with an active free subscription can request `planName="enterprise"`. Creation only checks that the plan exists, and authorization only checks subscription status, so the new key gets the enterprise rate without that subscription.
   - **Fix:** Derive the permitted plan from the authenticated user's subscription, using a domain policy; reject a supplied plan that disagrees if the request field must remain compatible. Test a free subscriber requesting an enterprise key.
   - **If we skip it:** Customers choose their own service limits and can multiply them by creating more keys.

7. **Direct API generation never consumes the advertised monthly allowance** (`src/main/kotlin/com/thridify/application/service/generation/submit/GenerateModelApplicationService.kt:28`, `src/main/kotlin/com/thridify/application/service/access/authorize/AuthorizeApiRequestApplicationService.kt:32`).
   - **What this is:** Plans publish a monthly generation quota and the schema has usage-period accounting.
   - **Problem:** Direct generation checks subscription status and per-key requests per minute, but never reads or increments the monthly allowance. A free subscriber can generate beyond the seeded 100-job monthly quota indefinitely at the allowed request rate.
   - **Fix:** Decide the direct plan's allowance-period rules, then atomically consume scope-level allowance with the job/outbox write through a domain repository port and policy. Test the last allowance, the next request, and two concurrent requests for one remaining slot.
   - **If we skip it:** Published quotas do not cap upstream GPU costs. The ADR already identifies direct quota enforcement as unfinished; Shopify's existing accounting does not cover this channel.

8. **Choosing free billing loses the reference to a still-paid Stripe subscription** (`src/main/kotlin/com/thridify/application/service/subscription/checkout/CreateCheckoutSessionApplicationService.kt:30`, `src/main/kotlin/com/thridify/infrastructure/persistence/PostgresSubscriptionRepository.kt:116`).
   - **What this is:** Free checkout replaces the scope's current subscription with an internal free subscription.
   - **Problem:** If a paid customer chooses free, the upsert overwrites the Stripe subscription/customer references without canceling the remote subscription. Stripe continues billing, the portal no longer has a customer reference, and later subscription-ID events cannot update the replaced row.
   - **Fix:** Reject this free activation while a live paid subscription exists and direct the customer through the existing billing portal cancellation flow. Preserve remote references until cancellation is confirmed and test paid-to-free checkout.
   - **If we skip it:** Customers can be charged for a subscription the application no longer displays or manages.

9. **Old or repeated Stripe events can overwrite newer billing state** (`src/main/kotlin/com/thridify/infrastructure/billing/StripeBillingClient.kt:52`, `src/main/kotlin/com/thridify/application/service/subscription/webhook/HandleStripeWebhookApplicationService.kt:23`).
   - **What this is:** Signed Stripe events change subscription status and plan access.
   - **Problem:** The adapter drops event IDs/timestamps and the handler has no durable duplicate or ordering check. A delayed `invoice.payment_failed` after payment recovery changes an active subscription back to `past_due`; a replayed old checkout can also replace the current plan.
   - **Fix:** Carry the provider event ID, record it atomically with the state change, and reconcile relevant current provider state or enforce a provider-aware ordering policy before applying stale events. Test duplicate delivery and failure-after-recovery ordering.
   - **If we skip it:** Legitimate webhook retries or reordering can deny paid access or restore an old entitlement.

10. **Admin price edits do not change the amount charged at checkout** (`src/main/kotlin/com/thridify/application/service/plan/updateplan/UpdatePlanApplicationService.kt:13`, `src/main/kotlin/com/thridify/infrastructure/billing/StripeBillingClient.kt:38`).
   - **What this is:** The admin can edit a plan's displayed price, while Stripe checkout uses its stored price offer.
   - **Problem:** Updating `priceCents` changes only the local plan and always passes a null price-offer update. A plan changed from 4900 to 9900 cents still uses the old Stripe price; changing a free plan to paid leaves it without any checkout price.
   - **Fix:** Create and store a replacement Stripe price when changing a paid amount, or reject price edits until that synchronization is supported. Test the updated plan's actual checkout price ID and the free-to-paid case.
   - **If we skip it:** The storefront advertises a price that checkout does not charge, or checkout stops working.

11. **Deactivated plans can still be bought or activated by ID** (`src/main/kotlin/com/thridify/domain/subscription/SubscriptionPolicy.kt:17`, `src/main/kotlin/com/thridify/application/service/apikey/create/CreateApiKeyApplicationService.kt:20`).
   - **What this is:** Admin deactivation hides an offer from the public active-plan list.
   - **Problem:** Checkout reads any plan by ID and the policy does not check `isActive`; key creation likewise accepts any existing plan by name. A customer with an old plan ID can still buy or activate it after deactivation.
   - **Fix:** Enforce active purchasable plans in the domain checkout policy and permitted entitlements in key creation. Test checkout of a deactivated free and paid plan while keeping existing subscriber behavior explicit.
   - **If we skip it:** Retired or internal-only plans remain selectable through direct API requests.

12. **Outbox rows can be marked published before RabbitMQ accepts the job** (`src/main/kotlin/com/thridify/infrastructure/outbox/OutboxRelay.kt:17`, `src/main/kotlin/com/thridify/infrastructure/messaging/TaskProducer.kt:31`, `src/main/kotlin/com/thridify/infrastructure/config/RabbitConfig.kt:22`).
   - **What this is:** The relay marks a database message delivered after the producer's send returns.
   - **Problem:** There is no publisher-confirm or mandatory-return handling in the repo. If the route has no bound queue, RabbitMQ can discard the message without a send exception, while the relay marks it published and leaves its job pending forever.
   - **Fix:** Require broker confirmation and reject unroutable sends before marking the row published. Add a real RabbitMQ test for an unbound route and a broker rejection; duplicate delivery remains safe through the existing dispatch reservation.
   - **If we skip it:** The transactional outbox protects database writes but does not guarantee that jobs reach a worker.

13. **Completion notifications are permanently lost on a crash or delivery error** (`src/main/kotlin/com/thridify/application/service/generation/callback/HandleGenerationCallbackApplicationService.kt:79`, `src/main/kotlin/com/thridify/application/service/generation/reconcile/ReconcileGenerationTasksApplicationService.kt:75`).
   - **What this is:** Completion commits the job and then sends the customer's HTTP notification.
   - **Problem:** A crash after commit or an HTTP failure leaves no durable delivery task; completed jobs are not retried for notification. `CallbackDeliveryTest` explicitly checks that a delivery failure is swallowed, and a repeated provider callback cannot reconstruct the pending delivery.
   - **Fix:** Write a notification outbox item in the same completion transaction and retry HTTP delivery in infrastructure, with a stable delivery identifier. Test failure/crash between completion and delivery; this follows DSA's rule that published messages use the outbox.
   - **If we skip it:** Customers waiting on webhooks never learn that some jobs finished even though polling shows success.

14. **Retrying a direct generation request creates another paid job** (`src/main/kotlin/com/thridify/application/service/generation/submit/GenerateModelCommand.kt:5`, `src/main/kotlin/com/thridify/application/service/generation/submit/GenerateModelApplicationService.kt:37`).
   - **What this is:** Direct submission creates a fresh job UUID for each POST.
   - **Problem:** If the transaction commits but the response is lost, resending the same request creates another job and upstream charge. The direct request carries no idempotency key, meaning a key that makes a repeated command return its original result without repeating side effects.
   - **Fix:** Accept an idempotency key and enforce a scope/key-scoped unique request lookup and job/outbox write in one transaction, following the existing Shopify flow. Test a repeated request and concurrent requests using the same key.
   - **If we skip it:** Clients cannot safely recover from uncertain responses. Handovers already document this limitation and prohibit automatic client retries; it remains a DSA command-idempotency gap.

## Should fix

15. **Failed direct submissions leave unowned photo uploads behind** (`src/main/kotlin/com/thridify/application/service/generation/submit/GenerateModelApplicationService.kt:31`).
   - **What this is:** Direct generation uploads photos before committing the job.
   - **Problem:** If upload two fails after upload one, or the database transaction fails after every upload, no cleanup runs. Unlike Shopify submission, direct submission leaves objects with no committed job reference.
   - **Fix:** Track uploaded keys and delete them when no job commits, using a domain deletion port as the Shopify path does. Test failure on a later upload and transaction rollback; if deletion fails, retain a durable cleanup record.
   - **If we skip it:** Storage costs and retained private photos accumulate after failures.

16. **Registration accepts invalid emails and empty passwords** (`src/main/kotlin/com/thridify/interfaces/rest/AuthController.kt:24`, `src/main/kotlin/com/thridify/application/service/identity/register/RegisterUserApplicationService.kt:24`).
   - **What this is:** The public registration route constructs an identity and hashes its password.
   - **Problem:** There is no input validation beyond duplicate-email lookup; `email=""` and `password=""` pass into persistence and password hashing. The domain identity model also has no construction checks, so internal callers get the same invalid identity behavior.
   - **Fix:** Validate email and password bounds in the domain identity policy before writes/hashing and apply protocol validation to reject malformed requests cleanly. Add empty-email, invalid-email, empty-password, and overlong-input tests.
   - **If we skip it:** The service creates accounts with unusable identity data and empty-password credentials; database length errors become server failures.

17. **Integration tests race with enabled background scheduling** (`src/test/kotlin/com/thridify/IntegrationTestBase.kt:30`, `src/main/kotlin/com/thridify/ThridifyApplication.kt:8`, `src/main/kotlin/com/thridify/infrastructure/observability/BacklogMetrics.kt:36`).
   - **What this is:** Integration tests share PostgreSQL and truncate tables before each case.
   - **Problem:** The fresh audit run failed `ShopifyLocalBillingTest.subscription works without Partner credentials or offers and reports local testing()` with a PostgreSQL deadlock during `resetDatabase()`. Scheduling is enabled unconditionally, and outbox/backlog schedulers remain active in cached test contexts; the failure proves concurrent table access, while the exact competing query was not captured.
   - **Fix:** Disable automatic scheduling for shared-database integration contexts and invoke job methods explicitly in their tests. Keep this control in the test setup or a scheduling configuration boundary and rerun the full suite.
   - **If we skip it:** CI can fail intermittently and background work can mutate fixtures outside the test being run.

18. **Direct job lists grow without a response bound** (`src/main/kotlin/com/thridify/infrastructure/persistence/PostgresJobRepository.kt:117`, `src/main/kotlin/com/thridify/infrastructure/persistence/PostgresJobRepository.kt:124`).
   - **What this is:** Dashboard and API job-list routes return a customer's entire job history.
   - **Problem:** Both queries fetch and map every matching row without a limit or cursor. A customer with 50,000 historical jobs makes each dashboard refresh load and serialize all 50,000, including attachment lookups; persistent history makes the cost increase over time.
   - **Fix:** Add bounded cursor pages through the existing query/result/controller path, using the Shopify model-list shape as the local pattern. Test page boundaries and stable ordering when jobs share timestamps.
   - **If we skip it:** Normal long-term use creates slow responses and avoidable database and heap pressure.

19. **Shopify business decisions live outside the domain** (`src/main/kotlin/com/thridify/application/service/shopify/attach/AttachShopifyModelsApplicationService.kt:25`, `src/main/kotlin/com/thridify/infrastructure/shopify/PostgresShopifyStoreRepository.kt:52`).
   - **What this is:** Attachment retry/terminal decisions and billing synchronization decide customer-visible state and allowances.
   - **Problem:** The application service owns attempt thresholds and status transitions, while the persistence adapter decides offer ambiguity, billing-period fallback, and quota behavior. For example, changing the attachment retry rule requires editing orchestration rather than a pure domain policy; the passing ArchUnit tests check dependencies but cannot detect this misplaced business logic.
   - **Fix:** Move these existing decisions into small value-based domain policies/state transitions, keeping loading, locking, and SQL in their current layers. Add focused policy tests; preserve the existing `ShopifyAllowancePolicy` and do not introduce a new framework or remove necessary repository ports.
   - **If we skip it:** DSA's business-rule boundary stays incomplete, and changing retries or billing rules risks changing persistence/orchestration behavior accidentally.

20. **Paid checkout mutates Stripe inside a database transaction without idempotency** (`src/main/kotlin/com/thridify/application/service/subscription/checkout/CreateCheckoutSessionApplicationService.kt:26`, `src/main/kotlin/com/thridify/infrastructure/billing/StripeBillingClient.kt:38`).
   - **What this is:** Paid checkout creates a remote Stripe session while the use-case transaction is open.
   - **Problem:** A slow Stripe call holds a database transaction/connection, and a successful remote call followed by a commit or response failure has no durable request identity to recover the created session. Plan creation has the related remote-first gap: creating Stripe product/price succeeds before a failed local insert, leaving orphan remote records.
   - **Fix:** Run remote mutations outside database transactions and pass a stable command/request identity through the billing port to provider idempotency, with a recorded result or compensation for local failure. Test remote success followed by local failure and repeated checkout/plan commands.
   - **If we skip it:** Latency consumes the connection pool and retries create extra external resources. Architecture docs explicitly preserved this old behavior during migration; it remains a documented exception to the requested DSA rules.

## Verification and architecture assessment

- Read README, architecture/system-flow docs, build/CI/container/local-service configuration, all migration schemas, the main authentication/API-key/billing/generation paths, Shopify connection/access/quota/attachment/privacy paths, their adapters, and focused tests. Read DSA's skill, template conventions, review checklist, and executable architecture rules.
- `./gradlew unitTest integrationTest spotlessCheck --console=plain` succeeded by reusing up-to-date tasks; this was not treated as fresh test execution.
- `./gradlew unitTest integrationTest --rerun-tasks --console=plain` compiled successfully and executed **110 unit/architecture tests: all passed**, then **105 integration tests: 104 passed, 1 failed** with the deadlock in finding 17. Formatting checks passed in the first command; the audit did not run a formatter.
- Fresh test log: `/private/tmp/3dify-audit-fresh-tests.log`. XML and HTML evidence: `build/test-results/unitTest/`, `build/test-results/integrationTest/`, and `build/reports/tests/integrationTest/`. The original working tree was clean.
- Passing DSA checks establish inward package dependencies, technology isolation, one public use case per service, no application-service chaining, and one service call per discovered entry point. They do **not** prove ownership, idempotency, transactional message delivery, domain rule placement, or safe external side effects.
- Existing DSA dialect permits Spring component annotations on policies and a neutral shared wire contract. These repo-sanctioned conventions were not reported as new violations. Single-implementation repository/client interfaces are necessary DSA boundaries and were not treated as bloat.
- Positive evidence: Shopify generation has scoped duplicate protection and atomic allowance consumption; attachments use leases and an ambiguity check before another product mutation; offline credentials are encrypted and bound to the installation; provider input decoding and retained-output downloads are bounded; output sources are restricted; privacy cleanup keeps failed requests pending; dispatch reserves work before a charged provider submission.

No smaller confirmed findings were omitted. No line/dependency savings estimate is claimed: this audit found correctness and boundary work, not a measured safe deletion opportunity.

Verdict: Fix cross-customer reads, webhook destinations, signing-key startup protection, photo forwarding, and revoked-key reactivation first; resolve billing/allowance and delivery guarantees before production use.

Not checked: Live Meshy/RunPod/Stripe/Shopify behavior, paid generation, real product mutations, production secrets/network/egress controls, dependency vulnerability feeds, full Git secret history, container-image availability/build, deployment/load/fault-injection tests, and the separate website/Shopify frontend repositories. Historical handoffs and every small DTO/test were not read line by line. The exact concurrent query in the integration deadlock was not captured; no application fixes or new regression tests were made in this audit.

## Resolution log

- Finding 17: Disabled automatic scheduling in integration contexts; added a regression check that no scheduling processor is registered.
- Finding 4: Relay forwards the full input list; regression cases cover 1, 3, 4, and 100 images through the actual relay and worker.
- Finding 1: Job details and history require the authenticated owner/key; cross-customer regression covers all three routes.
- Finding 3: JWT signing secrets are validated at startup; the development key is restricted to the sole local profile.
- Finding 2: Webhooks require public HTTPS destinations; delivery pins checked DNS addresses, verifies TLS hostnames, rejects redirects, and bounds connection/read/status parsing.
- Finding 5: Removed subscription-driven key activation/deactivation; revocation stays permanent and subscription authorization remains the access gate.
