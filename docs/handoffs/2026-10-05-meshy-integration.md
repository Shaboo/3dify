# Meshy integration handoff — 2026-10-05

Read this after the hardening/app handoff. This supersedes its RunPod-only generation setup and callback idempotence limitations.

## User decisions and completed work

Use Meshy initially, preserving contracts so a future owned RunPod worker can replace the adapter. User selected image-to-3D and then identified multi-image support. Existing two-image requests now use Multi-Image-to-3D; internal one-image calls use Image-to-3D. Shopify and backend hardening remain the scope; WooCommerce and broader workspace/staff/API-key work stay deferred. Annual Shopify subscriptions reset the monthly plan allowance each anchored month. User confirmed remote CI works; do not investigate it again without a new failure.

Meshy adapter, interchangeable registry/normalized result ports, persistent provider reservations and polling leases, owned output retention, privacy deletion and legacy RunPod callback compatibility are implemented. V3 backfills existing tasks and pins new tasks to their original provider. Definitely rejected capacity requests retry persistently; ambiguous POSTs never retry automatically. PNG/JPEG/WebP input compatibility remains. Completion and callback replay no longer create duplicate terminal transitions. Provider calls and downloads happen outside database transactions.

Backend: `~/Documents/3dify`, main, remote `git@github.com:Shaboo/3dify.git`. Commit message for this change: `Integrate Meshy behind interchangeable generation providers`. Shopify app: `~/Documents/3dify-shopify`, remote `git@github.com:Shaboo/3dify-shopify.git`, previously pushed main at `866fe22`. No app edits were necessary; public contracts remain unchanged.

## Validation

`./gradlew spotlessApply build` passed: 162 tests, zero failures/errors/skips. Coverage includes single/multi Meshy payloads and statuses, private inputs/WebP conversion, output host and stream limits, provider switching/pinning, duplicate dispatch, persistent capacity retries, ambiguous acceptance, lease exclusivity, failed storage retries, late completion after deletion, privacy 409 retry and callback replay. Integration tests use real PostgreSQL Testcontainers; external paid services are mocked. No deployment, real Meshy request, live Shopify install/charge or remote CI rerun occurred.

## Next session / deployment work

Follow [provider setup and recovery](../generation/PROVIDERS.md) and [Shopify launch acceptance](../shopify/LAUNCH.md). Meshy and generation polling are deliberately disabled by default; configure credentials, R2, `MESHY_ENABLED` and `GENERATION_POLLING_ENABLED` before generation. Keep old provider credentials until its tasks/privacy cleanup finish. Meshy outputs must be retained before their upstream expiration. Tune rate pacing for replica count/account limits. Manual reconciliation is required for interrupted submitting/uncertain tasks; do not erase reservations to force a replay.

Live acceptance needs a development store, actual plan offer handles, configured app/backend URLs, Meshy API credentials and owned public output storage. Test real GLB/USDZ generation, Shopify attachment/CORS and provider/privacy deletion. Customer webhook delivery remains best effort after commit; there is no durable delivery retry outbox for these notifications. The self-hosted inference container/model is still deferred; RunPod adapter contracts are ready for it.
