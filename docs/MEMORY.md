# Project memory

Updated 2026-10-07. Read alongside [the current handoff](handoffs/2026-10-07-session.md). These documents are persistent project context, not automatic model memory.

- Backend: `/Users/shaboo/Documents/3dify`, `git@github.com:Shaboo/3dify.git`, branch `main`.
- Standalone website: `/Users/shaboo/Documents/3dify-fe`, `git@github.com:Shaboo/3dify-fe.git`, branch `main`; handover `docs/HANDOFF.md`.
- Shopify app: `/Users/shaboo/Documents/3dify-shopify`, `git@github.com:Shaboo/3dify-shopify.git`, branch `main`.
- User uses IntelliJ and is unfamiliar with Node. Backend should run through **3dify Local**, without shell exports.
- Apply `.agents/skills/dsa/SKILL.md` for backend changes. Keep external mutations outside database transactions and preserve durable outbox/idempotency guarantees.
- Never display or commit credentials. Real backend config is ignored `config/application-local.properties`; frontend public config is ignored `.env.local`. VITE values are public.

## Intended behavior

Save a new product, then generate from its product page; existing products use the same panel. Merchant selects photos already on the product or uploads new photos. Generation is asynchronous; the backend automatically attaches the completed GLB even after the app closes.

Backend accepts arbitrary image lists with provider-specific bounds: Meshy 1–4, existing RunPod worker 1–2. Do not impose a global four-photo ceiling. Upload limits of 20 MB per photo and 85 MB per request are application limits, not verified Meshy limits.

Shopify store access/refresh tokens are separate from the Partner organization billing token. Required product scopes: `read_products,write_products`, with staff write authorization for product-bound generation. Offline credentials are exchanged/refreshed automatically and encrypted using a persistent local AES key.

Product binding, job, quota accounting, and outbox persistence remain atomic. Attachment uses durable leases and a job-specific media marker. Reconcile after ambiguous upload responses; do not blindly recreate media. Guard against old installations; uninstall deletes credentials and cancels pending attachments.

Live Shopify generation/attachment has not been verified. Mocked tests and builds must not be described as live E2E success.

Local billing bypass added 2026-10-07: `shopify.billing-mode=local-test`, only with the sole active `local` Spring profile, configured verified shop ID/domain and 10 generations per UTC calendar month. Real local settings are enabled for `3dify-test.myshopify.com`, Shop ID `85878767704`. No Partner billing calls in this mode; provider costs still apply. Preserve real authentication/permissions, durable quotas/idempotency, outbox and background attachment. An inactive internal plan has no purchasable offer mapping. Real billing remains blocked by Shopify App Store registration/pricing access and app visibility under the configured Partner organization. User accepts the product block's manual Refresh for saved photo changes; defer automatic refresh. Website and backend were started for user testing; check ports before launching duplicates.

Standalone website now has a complete Swiss visual redesign and direct generation workspace. User/admin roles remain enforced. Free plan activation does not require Stripe; actual provider credits still apply. New JWT GET `/dashboard/generation-options` reports provider photo limits. Website creates a tab-scoped API key, submits repeated multipart `images`, polls owned jobs with JWT and exposes downloads/errors. Never auto-retry uncertain direct generation POSTs; direct API has no HTTP idempotency key. Website has 24 fixture/browser checks; not live Meshy proof. Preserve sessions/local credentials. User requested handovers, commit and push of all current work in all three repos on 2026-10-07.
