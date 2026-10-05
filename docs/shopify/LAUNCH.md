# Shopify and generation launch setup

The backend and merchant app are implemented and validated with local tests. No deployment, real store installation, billing charge, GPU job or external media upload was performed in this session. Credentials and a development store are still required. Store secrets in environment/deployment settings, not Git or chat.

## Separate app

App repository: https://github.com/Shaboo/3dify-shopify, local directory `~/Documents/3dify-shopify`. Follow its README to configure the Shopify client ID, public embedded app URL and backend URL. The backend and frontend must reference the same Shopify app. Configure managed Shopify App Pricing and real offer handles. Register the uninstall and mandatory privacy webhooks at backend `/shopify/webhooks`; enable the documented frontend origins. Product selection/media attachment are app responsibilities.

## Billing and allowances

Map real pricing handles into `plan_offers` with `provider = 'shopify'`, the correct plan ID and `billing_interval = 'monthly'` or `'yearly'`. A monthly offer receives one plan allowance per provider cycle. An annual offer receives that same monthly allowance each calendar month anchored to the annual cycle start. January 31 anchors February to its final day and March back to March 31. No rollover; an accepted job consumes one generation even if it fails. Existing consumed usage is retained, and a downgrade never reduces a window below already consumed/reserved quota. Configure the intended plan limits explicitly.

Partner lifecycle state is read over explicit windows of at most 365 days, searching backward until a creation/cancellation/freeze/unfreeze event is found. A current-year empty window does not clear older frozen status. Long quiet subscriptions can require several rate-limited queries; validate actual provider history availability and permissions in the development store.

## Meshy and owned output storage

Meshy is the default; follow [generation provider setup](../generation/PROVIDERS.md) to enable credentials and polling. Existing two-image requests use Multi-Image-to-3D. No owned inference worker is needed. Configure R2 below for private inputs and durable output copies. Keep provider credentials available for pending tasks and privacy deletion.

## Optional future RunPod worker and R2

Set `RUNPOD_ENABLED=true`, `RUNPOD_API_URL=https://api.runpod.ai/v2/<endpoint>/run`, `RUNPOD_API_KEY` and `RUNPOD_WEBHOOK_URL=https://<backend>/internal/webhooks/runpod`. Requests include `input.jobId`, `input.image1`, `input.image2`, and `input.outputPrefix = outputs/<jobId>/`, plus the callback URL including job ID. Inputs are R2 object keys, not public input image URLs. Provision a worker that reads the private configured bucket using worker-side credentials, runs the selected image-to-3D model and writes both formats under that output prefix. The inference model/container is not provisioned by this repository.

The worker must return `output = {"glb": "https://<public-storage>/outputs/<jobId>/model.glb", "usdz": "https://<public-storage>/outputs/<jobId>/model.usdz"}`. RunPod posts its job ID/status/output to the supplied callback. The backend accepts only the task/job association it recorded. Missing output formats, cancellation and timeout are failed outcomes. The provider adapter validates HTTPS endpoint/callback configuration, uses bounded HTTP timeouts and never returns simulated output URLs.

Set `R2_ENDPOINT`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_BUCKET`, and `R2_PUBLIC_URL` (public output origin, optionally a path prefix). Give the worker storage access through its deployment secrets. Keep inputs private. Allow the embedded app origin to fetch output GLB files through storage CORS so the app can upload them to Shopify staged storage. Public output URLs must use the managed layout; privacy cleanup refuses to delete unknown origins/layouts and leaves the receipt pending for operator correction. Configure output retention in the worker/provider and verify the final physical deletion in a live test. Shopify's attached media is separately owned by Shopify/the merchant after upload.

RunPod async result metadata expires independently from durable bucket outputs. Do not use temporary provider result URLs as the durable product model source. Official request/webhook contract: https://docs.runpod.io/serverless/endpoints/send-requests. Official historical window limits: https://shopify.dev/docs/api/partner/latest/queries/events.

## Live acceptance

1. Configure one development app/store with actual offer mappings, backend/app URLs, direct Admin API product permissions and webhook destinations.
2. Open the embedded app and verify installation, token refresh/retry, hosted plan selection and authoritative subscription/quota display.
3. Generate one real GLB/USDZ pair; verify private input submission, polling completion, retained outputs and output CORS/download.
4. Select a product, attach the GLB and wait for Shopify media processing; view the product model. Repeat with the app's retry behavior.
5. Exercise frozen/unfrozen, cancel, uninstall/reinstall and privacy cleanup; check another store stays unaffected and confirm output object removal.
6. Revalidate monthly annual allowance boundaries and actual provider history permissions. Deploy only after these checks pass.

WooCommerce, shared staff authorization, multi-workspace selection and broader scoped API-key authorization remain outside this task by user decision.
