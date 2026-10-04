# 3dify system flows

The current backend follows [DSA](architecture.md). Protocol adapters call one application service per operation. Package namespace: `com.3dify` (escaped as the escaped numeric package component in Kotlin source).

## Endpoints and use cases

| Endpoint | Application service | Authentication |
|---|---|---|
| `POST /auth/register` | RegisterUser | Public |
| `POST /auth/login` | LoginUser | Public |
| `GET /public/plans` | ListActivePlans | Public |
| `GET /admin/plans` | ListPlans | JWT + administrator |
| `POST /admin/plans` | CreatePlan | JWT + administrator |
| `PUT /admin/plans/{id}` | UpdatePlan | JWT + administrator |
| `DELETE /admin/plans/{id}` | DeactivatePlan | JWT + administrator |
| `POST /dashboard/api-keys` | CreateApiKey | JWT |
| `GET /dashboard/api-keys` | ListApiKeys | JWT |
| `DELETE /dashboard/api-keys/{keyId}` | RevokeApiKey | JWT |
| `GET /dashboard/subscription` | GetSubscriptionStatus | JWT |
| `POST /dashboard/subscription/checkout` | CreateCheckoutSession | JWT |
| `POST /dashboard/subscription/portal` | CreateBillingPortal | JWT |
| `GET /dashboard/webhooks` | GetWebhook | JWT |
| `PUT /dashboard/webhooks` | SetWebhook | JWT |
| `DELETE /dashboard/webhooks` | DeleteWebhook | JWT |
| `GET /dashboard/jobs` | ListUserJobs | JWT |
| `GET /dashboard/jobs/{jobId}/history` | GetJobHistory | JWT |
| `POST /api/v1/generate` | GenerateModel | API key |
| `GET /api/v1/jobs` | ListApiKeyJobs | API key |
| `GET /api/v1/jobs/{jobId}` | GetJob | API key |
| `GET /api/v1/jobs/{jobId}/history` | GetJobHistory | API key |
| `POST /webhooks/stripe` | HandleStripeWebhook | Stripe signature |
| `POST /internal/webhooks/runpod/{jobId}` | HandleGenerationCallback | Provider task/job correlation |

All names in the table have the `ApplicationService` suffix. Creation returns the existing 201 or 202 status, deletions return 204, and the existing JSON fields remain unchanged.

## Authentication

JWT authentication verifies the token via the token port and looks up administrator status through the user repository. The filter maps the application result into Spring authorities. Administrator status is read from PostgreSQL, rather than trusted from a token role claim.

API authentication reads `X-API-KEY`, checks its prefix/hash and active flag, loads the subscription, checks eligibility, then consumes a PostgreSQL-backed Bucket4j token. Invalid keys produce 401, absent/ineligible subscriptions 403, and exhausted rate limits 429. Subscription rejection does not consume rate-limit capacity. The API principal remains the API-key ID; the dashboard principal remains the user ID.

## Generation lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant A as Application services
    participant S as Image storage
    participant D as PostgreSQL
    participant R as RabbitMQ
    participant G as GPU provider
    participant W as Customer webhook
    C->>A: GenerateModelCommand with two images
    A->>S: Upload both images
    A->>D: Transaction: pending job, history, task outbox
    A-->>C: 202 with jobId and PENDING
    A->>D: Poll up to 50 pending outbox messages every 500 ms
    A->>R: Deliver stored generation task
    A->>D: Mark successful publication
    R->>A: DispatchGenerationTaskCommand
    A->>D: Record processing and history
    A->>G: Start generation
    G-->>A: External task ID
    A->>D: Store task ID and dispatch history
    G->>A: Provider callback
    A->>D: Verify external task/job association
    A->>D: Record success or failure and history
    A->>W: Attempt configured customer notification
```

Empty images fail before storage access. An upload failure creates no job. If job/history/outbox persistence fails, the database work rolls back together; previously uploaded images remain, matching the existing behavior.

Dispatch errors record `FAILED` and do not notify the user. Completion callbacks reject a mismatched task ID with 400. Completed outputs default absent GLB/USDZ URLs to empty strings; a completed callback with no output object remains in progress. Failed callbacks use the existing GPU-failure text. Database-processing exceptions produce 500; customer notification failures are measured and swallowed after recording the job outcome.

The customer notification JSON remains `jobId`, `status`, `outputGlbUrl`, and `outputUsdzUrl`. Task wire JSON remains `jobId`, `inputImage1Key`, and `inputImage2Key`.

The development RunPod adapter generates a mock task ID and simulates a completion callback after a delay. Real dispatch remains commented out in that adapter.

## Billing

Free checkout upserts an active subscription, changes the plan for the user's active API keys, records activation, and returns the requested success URL. Paid checkout requires a linked Stripe price and creates a subscription-mode session. It retains the success-URL suffix `?session_id={CHECKOUT_SESSION_ID}` and `userId`/`planId` metadata. Billing portal creation requires a subscription with a linked Stripe customer.

| Stripe event | Effect |
|---|---|
| `checkout.session.completed` | Retrieve subscription state, upsert it, update active API-key plans, measure activation |
| `customer.subscription.updated` | Update status/period end; reactivate API keys only for `active` |
| `customer.subscription.deleted` | Mark canceled; deactivate the associated user's keys |
| `invoice.payment_failed` | Mark the customer's subscriptions past due; preserve current key activation flags |
| Other event | Acknowledge without state changes |

Signature verification and Stripe SDK model mapping belong to infrastructure. The application service receives typed billing events. Paid plan creation creates a Stripe product/monthly price before inserting the local plan. Updating local prices does not change Stripe prices, matching existing behavior.

## Persistence and follow-ups

Flyway V1–V12 define nine application tables: users, plans, api_keys, jobs, webhooks, outbox_messages, job_history, subscriptions, and rate_limits. PostgreSQL adapters preserve existing SQL and map records to domain models; SQL records never cross the adapter boundary. The migration does not change database schemas, broker identities, API-key prefixes, configuration namespaces or metric names. Existing external identities still use `omni3d` where configured; the code namespace is `com.3dify`.

Duplicate handling, checkout compensation/transaction timing, atomic worker/callback writes, and job ownership checks are separate behavior changes in the [migration findings](docs/dsa-migration/PLAN.md). The [workspace/tenant ADR](docs/adr/0001-use-workspace-tenant-model-for-omnichannel.md) remains the direction for future Shopify/omnichannel ownership and billing.
