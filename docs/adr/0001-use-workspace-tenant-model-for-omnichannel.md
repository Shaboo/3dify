# 1. Use Workspace/Tenant Model for Omnichannel and Shopify Integration

Date: 2026-10-03

## Status
Accepted

## Context
Our platform needs to evolve from serving only standalone API developers (who register with email/password and pay via Stripe) to supporting Shopify merchants. Shopify merchants authenticate via Shopify OAuth (session tokens) and are billed via the Shopify Billing API. 

Currently, our core resource hierarchy is strictly tied to a `User` identity (e.g., `User` -> owns -> `API Keys`, `Jobs`, `Subscriptions`). 
If we cram Shopify shops into the `User` table, we will introduce fragile branching logic for authentication, billing, and resource ownership. Furthermore, if a merchant wants to use both our Shopify app and a custom mobile app, they would struggle to share quotas and jobs seamlessly under the current model.

## Decision
We will decouple Identity from Resource Ownership by introducing a **Workspace (Tenant)** model:
1. **Workspaces**: All domain resources (Jobs, API Keys, Subscriptions, Webhooks) will belong to a `Workspace`, not a `User`.
2. **Identities & Platform Connections**: A Workspace can be accessed by multiple identities (e.g., Email/Password users) or linked to external platform connections (e.g., a Shopify Shop installation).
3. **Billing Abstraction**: Subscriptions will be linked to the Workspace, and a `BillingProvider` abstraction (Stripe vs. Shopify) will handle the payment lifecycle based on the Workspace's origin or configuration.

## Consequences
- **Positive**: Seamlessly supports Shopify's OAuth and Billing alongside our direct Stripe/JWT integration. Prepares the system for other platforms (WooCommerce, Salesforce). Merges omnichannel usage perfectly.
- **Negative**: Requires a significant database schema migration and refactoring of our security and service layers to check `Workspace` access rather than just `userId`.
