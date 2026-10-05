# Session handoff — Shopify hardening and merchant app, 2026-10-05

## User decisions and scope

- User confirmed remote CI is working. Do not spend time rechecking or changing it without a new failure.
- Complete the concrete remaining Shopify/backend gaps and create/push a separate Shopify-facing app to `git@github.com:Shaboo/3dify-shopify.git`, outside the backend under `~/Documents`.
- User clarified broader multi-workspace selection, staff authorization, scoped API-key authorization and WooCommerce remain future work.
- Annual Shopify plans reset the plan's monthly allowance each calendar month of the annual subscription; no annual lump sum.
- Record all work for the next session. Existing authorization for backend commits/pushes persists. New app creation and push explicitly authorized. No deployment was requested/performed; no live credential material supplied.

## Repositories

Backend: `~/Documents/3dify`, namespace `com.thridify`, project spelling **thridify**, remote `git@github.com:Shaboo/3dify.git`, branch `main`.

Merchant app: `~/Documents/3dify-shopify`, remote `git@github.com:Shaboo/3dify-shopify.git`, branch `main`. It is an embedded Vite/TypeScript app with current App Bridge/Polaris web components and online Direct Admin API access. Product selection, staged GLB upload, attachment and merchant UI remain outside the backend. Read its `docs/HANDOFF.md` and README.

Previous pushed backend commits: `3732941` (store-scoped Shopify) and `13386f9` (parallel CI/Testcontainers). This session's backend changes are committed under “Harden Shopify quotas, billing lifecycle and provider integration”. Consult git log for exact hashes and latest push state; final session response records the outcome. The previous session handoff is now included in Git for historical context.

## Backend changes

1. Concurrent idempotency: `ShopifyGenerationRepository.lock(store)` serializes on the connection row before duplicate/quota resolution in both preflight and final transaction. Same-key requests racing at the last allowance return the same job; one charge, one job/history/outbox, losing uploads cleaned. Repository insertion retains its lock and duplicate lookup. Two-thread barrier integration test exercises the actual race.
2. Annual allowances: pure `ShopifyAllowancePolicy` calculates calendar months anchored to original cycle start, correctly handling January 31/leap February/exact boundaries and expired subscriptions. Monthly offers retain provider cycles. Subscriptions keep provider dates; usage periods get independent monthly dates. Usage consumption selects the latest active allowance. Subscription API adds nullable ISO `allowancePeriodStart` and `allowancePeriodEnd` alongside provider `periodEnd`. Prior usage is preserved. No new Flyway migration was needed.
3. Billing lifecycle: explicit historical windows of at most 365 days searched newest-first backwards to 2006 until creation/cancellation/freeze/unfreeze is found. Every call is rate limited (default 300 ms). Updated/scheduled-cancel events do not erase freeze state. Malformed/missing lifecycle or dates fail closed. Native managed subscription IDs remain nullable.
4. HTTP handling: empty, malformed and non-object JSON => 502; GraphQL ACCESS_DENIED/THROTTLED/UNAUTHENTICATED => 403/503/401. Interrupted Shopify HTTP requests preserve interrupt state. Invalid UUID/missing multipart fields => 400; upload excess => 413. Shopify image uploads accept JPEG/PNG/WebP up to 20 MiB; servlet configuration 20 MB/file and 42 MB/request aligns with app.
5. Real provider dispatch: RunPod simulation removed. Adapter disabled by default; configured `/run` HTTPS endpoint and public HTTPS callbacks produce real provider IDs. Bounded timeouts, accepted-status/ID validation, managed `outputs/<jobId>/` prefix. Inputs remain private bucket keys. Secrets configured through env. Mock HTTP tests cover payload, disabled dispatch, malformed acknowledgments. No real GPU worker/model/container provisioned; worker contract documented.
6. Callback outcomes: missing completed GLB/USDZ, TIMED_OUT and CANCELLED become failures; no empty successful outputs or indefinitely processing terminal jobs. Historical characterization tests were intentionally updated to the new behavior.
7. Privacy: input deletion plus generated output deletion precede scoped record deletion. Output failures keep redaction pending for retry; other stores untouched. `R2_PUBLIC_URL` binds deletion to configured output origin/path and managed layout. Unknown provider-hosted outputs are retained with a pending receipt rather than silently claiming cleanup. Output ownership and retry regressions added.
8. Docs: README, architecture/system flows, ADR, migration status, Shopify implementation and `docs/shopify/LAUNCH.md` updated. Historical migration entries remain as history; current handoff supersedes old “simulation/annual policy pending” claims.

## Validation

Final `./gradlew spotlessApply build` passed with **140 tests across 31 suites**, zero failures/errors/skips. This includes strict architecture rules and PostgreSQL Testcontainers integration tests. `git diff --check` passed. No architecture exemptions introduced. Tests use Docker; external Shopify, storage and generation providers are mocked. Production RunPod adapter tested using Spring mock HTTP requests. Previous CI is working per user; no remote CI inspection done this session.

App validation is recorded in its handoff: unit/contract tests, TypeScript/Vite production build and a mocked Chromium merchant flow. Root integration review found and corrected canonical backend job statuses: backend emits **SUCCESS**, while RunPod callbacks use COMPLETED. Mock browser fixtures must use backend SUCCESS. Upload limits were aligned with app. The app refreshes Shopify ID tokens, preserves the generation UUID/files on retry, and checks product media before attachment retries. Concurrent product attachments across tabs remain best effort (no atomic Shopify-side application marker). App review also includes the distinction between monthly allowance renewal and provider billing end.

## Live setup still required

Read `docs/shopify/LAUNCH.md`, then the app README. No secrets/development store/real plan handles/provisioned inference worker are available. Shopify and RunPod remain disabled by default. No deployment, real charge, GPU inference or live media upload occurred.

Configure the same Shopify app identity in both repos, real hosted offers and Partner permissions, direct product access/scopes, webhook destinations, origins/CORS, worker endpoint and output bucket. Provision a worker implementing the documented private-input/managed-GLB-USDZ contract. Verify real token exchange, frozen/unfrozen/annual periods, output CORS, attachment processing and physical privacy deletion. Provider retention and selected inference model behavior require live validation; these cannot be claimed complete without access.

## Retained out-of-scope findings

Broader workspace/WooCommerce roadmap is explicitly deferred. Historical direct-user/Stripe dispatch/callback deduplication and transaction/compensation findings in the DSA migration plan remain separately scoped; this session does not claim those general flows are fully redesigned. Repeated provider callbacks can still repeat metrics/history/customer delivery in the pre-existing general callback use case. Real-provider dispatch acknowledgment versus callback ordering must be validated against the worker; no generic provider outbox/compensation redesign was added.

There should be no active implementation work at handoff. Check `git status` in both repos and the final session response for confirmed pushes before resuming.
