# ADR 0001: Workspace ownership with independent billing scopes

Date: 2026-10-04

## Status

Accepted design for the fresh development schema. This document replaces the earlier workspace ADR; there is no deployed database or migration compatibility requirement.

## Context

Thridify will launch first as a paid Shopify app, then support WooCommerce and customers integrating their own websites through the API. A person may manage multiple stores, and multiple people may manage one account. A store installation is neither a person nor a paid subscription.

The previous schema made users own API keys, subscriptions and webhook settings, and embedded Stripe subscription/customer/price identifiers in core tables. It could not express independent subscriptions and usage allowances for two stores within the same customer account.

## Decision

### Identity and ownership

A **workspace** is the tenant and owns resources. A **user** is a person. A **membership** associates a user with a workspace and a role (`owner`, `admin`, `member`). Direct registration creates a workspace, owner membership and direct billing scope atomically. A default membership supports the existing direct-user API while explicit workspace context is implemented.

Shopify installation identifies a verified shop connection and resolves its workspace; it does not require email/password registration. Store staff authentication must verify Shopify session claims and authorization, then resolve trusted workspace/connection context. Do not infer workspace membership, transfer ownership or merge accounts from matching email addresses or a caller-supplied store URL.

A **platform connection** identifies a Shopify shop or WooCommerce site independently of people and subscriptions. Each `(platform, external_id)` belongs to exactly one workspace. Use the stable provider store identifier; URL/domain is metadata. Reinstallation resolves the existing connection rather than creating another account. Store credentials are infrastructure concerns and must be encrypted or held in secret storage.

### Billing and entitlements

Workspace ownership and billing scope are separate:

- A direct API/website customer has one direct billing scope within its workspace.
- Each connected Shopify store has its own billing scope, purchased subscription and generation allowance.
- A workspace may contain multiple stores and therefore multiple independent subscriptions.
- WooCommerce connections can use the same scope model; their payment provider is a separate choice and initially can be Stripe.
- Cross-store credit pooling, one subscription covering multiple stores, and a combined Shopify/Stripe allowance are not part of the initial product. They require a later commercial decision.

A **plan** defines product benefits such as generation allowance and request limits. A **plan offer** maps a plan to a provider-specific purchase identifier and interval. Plan `price_cents`/`currency` are the existing direct storefront's reference display price; Shopify's provider contract determines Shopify's actual charge. The database seeds product plans, not real provider offers or active paid subscriptions.

A **subscription** belongs to a billing scope and stores normalized status, provider, generic external references and billing period. There is at most one current subscription per scope; canceled/expired subscriptions remain history. Free direct plans use the `internal` provider and carry no external billing identifiers. Shopify and Stripe external identifiers are namespaced by provider.

Access decisions use local normalized subscription state and plan benefits. Paid entitlement activation requires verified provider state, never only a success redirect or plan handle from the browser. Installation state, subscription state and access policy are separate. A disconnected Shopify connection must not continue using store-scoped access simply because a cached subscription was active.

A **usage period** belongs to a billing scope, snapshots its allowance and records reserved/consumed generations for its confirmed period. Consumption and reservations must be atomic; retrying one job must not charge it twice. Shopify store allowances remain independent. This schema supplies accounting storage; runtime quota enforcement and per-job idempotency are subsequent implementation work. Reset semantics for free plans and upgrades must be decided before enabling usage enforcement.

### Shopify launch

Use Shopify App Pricing when the chosen pricing model is supported. Shopify hosts plan selection; the app confirms the subscription through the appropriate Partner API and maps its plan handle to an internal plan. The legacy manual Billing API remains a fallback for unsupported pricing models, not an assumed implementation.

Subscription changes require verified notifications and periodic authoritative reconciliation. Deduplicate events and avoid applying stale state; handle trials, frozen billing, cancellation and uninstall independently. The Shopify implementation must include installation/session verification, credential lifecycle, reinstall handling and required privacy webhooks before launch.

Provider integration references:

- [Shopify App Pricing](https://shopify.dev/docs/apps/launch/billing/shopify-app-pricing)
- [Shopify authentication and authorization](https://shopify.dev/docs/apps/build/authentication-authorization)
- [Shopify per-store billing management](https://help.shopify.com/en/manual/organization-settings/billing/manage-store-billing)

### DSA boundaries

Business use cases have separate application services, for example connecting a store, resolving workspace access, synchronizing a subscription and reserving generation allowance. Application services orchestrate domain policies and intent-based ports. They do not call another application service or expose provider wire types.

Domain owns workspace/membership policies, normalized subscription and entitlement decisions, and publisher/repository/client contracts. Infrastructure owns Shopify/Stripe calls, SQL, credentials, serialization, verified event decoding and delivery/reconciliation machinery. Thin business entry points map protocol input to one application service. The technical outbox remains entirely in infrastructure; application services only call the generation publishing port.

Do not force Shopify into Stripe's checkout-session or billing-portal vocabulary. Share stable subscription/access concepts while keeping provider-specific purchase and management capabilities explicit.

### Fresh schema and implementation boundary

Replace the retired twelve migrations with `V1__initial_schema.sql`. Existing development databases with the old Flyway history must be recreated; do not repair checksums or pretend this baseline upgrades the old schema.

The fresh schema contains workspaces, memberships, platform connections, billing scopes, plans/offers, subscriptions, usage periods, keys, jobs, workspace webhooks and infrastructure tables. Composite foreign keys reject keys/jobs referencing another workspace's billing scope or another scope's key. API keys may record their creating user, but that attribution is not ownership. Jobs can exist without an API key for future verified platform entry points.

The existing direct-user HTTP API remains operational through its default workspace/direct scope. Some domain DTOs and port methods still use `userId` or Stripe vocabulary as a transitional boundary. This does not implement a Shopify app, multi-workspace selection, shared staff access, or quota metering. Those are explicit next steps rather than completed features.

## Consequences

- Multiple stores can share account administration without implicitly sharing subscriptions or credits.
- New platform adapters can reuse generation and subscription concepts.
- Direct signup must provision its workspace and billing scope atomically.
- Authorization must eventually carry explicit workspace, connection and billing-scope context through every use case; API-key creator identity is insufficient for that future flow.
- Billing is more complex than one subscription per user; cancellation must affect only the relevant scope.
- The schema reset discards migration history. Only fresh development databases are supported by this baseline.

## Deferred decisions

Final Shopify pricing and allowance sizes; free-plan period boundaries; usage reservation/release/refund semantics; upgrade/downgrade treatment; WooCommerce distribution and billing; cross-store sharing; workspace transfer/merging; data retention after uninstall; mapping Shopify staff permissions to workspace roles.
