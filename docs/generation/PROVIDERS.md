# Generation providers

Meshy is the default adapter. Public generation requests, quota accounting, job statuses and GLB/USDZ response fields are unchanged. The existing two-image request uses Meshy Multi-Image-to-3D. The internal adapter also supports a single image with Image-to-3D; this does not relax the public API validation or add a four-image public endpoint.

## Enable Meshy

Set these deployment environment variables:

```text
GENERATION_PROVIDER=meshy
GENERATION_POLLING_ENABLED=true
MESHY_ENABLED=true
MESHY_API_KEY=<deployment secret>
MESHY_AI_MODEL=meshy-7.1
R2_ENDPOINT=<S3-compatible endpoint>
R2_ACCESS_KEY=<deployment secret>
R2_SECRET_KEY=<deployment secret>
R2_BUCKET=<bucket>
R2_PUBLIC_URL=https://<owned output origin>
```

Configure the existing database/RabbitMQ and Shopify settings as described in the launch runbook. Generation and polling are disabled by default until explicitly enabled. No RunPod worker or Meshy webhook is required for Meshy. Meshy requests ask for textured GLB and USDZ. The model defaults to pinned `meshy-7.1`; supported configuration values also include `meshy-6-lite`, `meshy-6`, and `latest`.

Input objects stay private in R2. The adapter reads them and sends inline data URIs to Meshy. PNG/JPEG are preserved and WebP is converted to PNG. Input limits are 20 MiB per object and 32 million pixels. The default adapter request interval is 500 milliseconds; tune `generation.meshy.request-interval-ms` to account limits and deployment concurrency. Pacing is per process, not a distributed rate limiter. HTTP 429 and Retry-After are handled conservatively through persisted submission retries and polling.

Configure `generation.meshy.request-timeout-seconds` for image submission and task requests (default 120 seconds). Base64 inputs increase request size; large photo submissions can exceed short timeouts. Transport logs report exception class and timeout detection, and HTTP rejection logs report status only. Credentials, upstream bodies and signed URLs are not logged. A timed-out submission remains uncertain and is never automatically resubmitted.

Meshy temporary output URLs are downloaded to owned R2 keys `outputs/<jobId>/model.glb` and `model.usdz` before SUCCESS is published. Downloads allow only configured HTTPS asset hosts, reject redirects and have bounded size/time. Default Meshy host: `assets.meshy.ai`. Each asset is limited to 500 MiB. Set output storage CORS for the merchant app. Standard Meshy assets expire after three days, so keep polling healthy and alert on stuck PROCESSING jobs.

## Durable lifecycle and recovery

Migration V3 creates `generation_provider_tasks` and backfills historical task IDs as RunPod tasks. Each task pins its provider, so changing the default only affects new jobs. External IDs are now provider-qualified internally; public responses do not expose this representation. Database reservations prevent duplicate RabbitMQ deliveries from resubmitting accepted work. Polling leases prevent simultaneous reconciliation of the same task. Completion and the existing authenticated RunPod callback are idempotent and respect Shopify redaction locks.

States: `submitting` reserves a POST; `submitted` has an upstream handle; `retrying` is an explicit capacity rejection scheduled for another attempt; `uncertain` means acceptance could not be established; `failed` and `complete` terminate provider tracking. Known capacity rejection keeps the local job PROCESSING. A timeout, server error or malformed acknowledgement is never blindly resubmitted, because the upstream task may already be chargeable. Interrupted `submitting` and `uncertain` rows require operator reconciliation.

For recovery, inspect the job/provider row and sanitized backend logs. When an acknowledged handle failed to persist, the error log records provider, handle and local job ID. Verify the task with the provider before repairing anything. Meshy handles have form `multi-image-to-3d:<id>` or `image-to-3d:<id>`; the corresponding job external ID has an additional `meshy:` prefix. Repair the job association and provider row together in a transaction and restore PROCESSING/submitted with a due poll only after verified acceptance. Never clear the reservation or replay a POST merely because a task ID is absent. If acceptance remains unknown, keep the row for manual investigation. No automated recovery command is provided.

Shopify privacy cleanup deletes upstream tasks and both canonical owned output objects before local records. Meshy refuses deletion while IN_PROGRESS (409), so cleanup remains pending and retries. Expired/absent upstream tasks (404) are treated as already removed. Unknown submissions deliberately block cleanup until reconciled. Shopify media uploaded by the merchant remains separately owned by Shopify.

## Switch to RunPod or add an adapter

Set `GENERATION_PROVIDER=runpod`, enable RunPod and configure its endpoint, key, callback URL and worker as described in the launch runbook. Keep polling enabled. Retain Meshy credentials while any old Meshy tasks or privacy receipts remain pending; provider selection does not migrate in-flight tasks. RunPod-owned outputs must follow the same canonical R2 layout. Legacy raw RunPod task associations and callbacks remain accepted.

For another API, implement the domain `GenerationProviderClient` port (`name`, `startGeneration`, `retrieveTask`, `deleteTask`) as an infrastructure component. Return opaque handles and normalized domain results; classify POST failures as definite or ambiguous, and flag only definitely rejected requests as retryable. Add any external output origins under `generation.asset-hosts.<provider>`. The registry discovers adapters and the shared application lifecycle owns reservation, polling, retention and completion. Controllers, Shopify app, job contracts and quota rules do not change.

Official contracts: [Image-to-3D](https://docs.meshy.ai/en/api/image-to-3d), [Multi-Image-to-3D](https://docs.meshy.ai/en/api/multi-image-to-3d), [asset retention](https://docs.meshy.ai/en/api/asset-retention), [rate limits](https://docs.meshy.ai/en/api/rate-limits). No live paid generation was made during implementation.
