---
name: 3dify-dsa
description: Enforce 3dify's Domain Service Architecture (DSA), Domain-Driven Design boundaries, and Kotlin/Spring Boot implementation rules. Use whenever Codex plans, writes, changes, reviews, refactors, tests, or scaffolds code for the 3dify repository, including modules, APIs, workers, persistence, events, verification engines, SDK-facing endpoints, architecture decisions, and pull-request reviews.
---

# 3dify DSA Engineering

Use the supplied Domain Service Architecture as a mandatory engineering constraint for 3dify. Preserve its four layers, one-use-case application services, pure domain model, dependency inversion, API-contract separation, and explicit I/O boundaries.

## Required context

Read [references/3dify-dsa-profile.md](references/3dify-dsa-profile.md) completely before making or reviewing a 3dify code change. It contains the adopted dependency rules, 3dify module profile, naming conventions, transaction rules, and verification checklist.

Also inspect repository-local `AGENTS.md`, architecture decision records, Gradle settings, and architecture tests. Repository instructions may add stricter rules, but must not weaken this skill without an explicit user decision recorded as an ADR.

## Engineering workflow

1. Name the business capability and use case before selecting files or frameworks.
2. State the domain rules and invariants separately from orchestration, transport, and technology concerns.
3. Select the owning domain-model group and layer using the placement table in the reference.
4. Define or update the public contract independently of domain models.
5. Implement the domain behavior without infrastructure dependencies.
6. Implement one application-service method that owns the complete use case and logical transaction boundary.
7. Add thin interface adapters and infrastructure implementations around domain ports.
8. Add unit, integration, contract, and ArchUnit coverage appropriate to the change.
9. Run the narrowest relevant tests first, then the repository verification suite.
10. Report any deliberate exception as an ADR candidate; never hide boundary erosion as convenience code.

## Non-negotiable rules

- Use one Gradle/Spring Boot project. Under `com.3dify`, allow exactly five production package roots: `application.service`, `infrastructure`, `domain`, `interfaces`, and `shared`.
- Group capabilities and sub-capabilities below each layer root. Do not create per-layer or per-capability Spring/Gradle subprojects.
- Keep the domain layer independent of Spring, persistence, serialization, transport, cloud, and ML vendor APIs.
- Put entities, value objects, domain services, factories, validators, events, and technology-neutral ports in `domain`.
- Do not inject repositories, publishers, clients, clocks that perform I/O, or other ports into domain services. Application services make I/O visible.
- Put business rules and decisions in domain objects or domain services, never in controllers, listeners, repositories, mappers, or application services.
- Make each application-service method one complete use case and one logical transactional boundary.
- Never call one application service from another. Compose the required domain objects and ports in a single owning application service.
- Keep interfaces thin: validate transport shape, map contract models to commands, invoke one application-service method, and map its result.
- Never expose domain models in HTTP, message, SDK, or other external contracts.
- Never let an interface adapter call a repository, publisher, client, or domain service directly.
- Implement domain ports in infrastructure. Use generated jOOQ tables and `DSLContext` for PostgreSQL access; do not add handwritten JDBC repositories, SQL-string persistence, or row mappers. Keep object-store SDKs, cryptography providers, model runtimes, brokers, and HTTP clients there.
- Do not hold a database transaction open across external network or model-service mutations. Use explicit workflows and compensation where needed.
- Publish domain/integration messages transactionally through an outbox. Assume at-least-once delivery and make consumers idempotent.
- Keep public API/message schemas under the owning `interfaces` capability package. Never place contracts outside the five approved roots.
- Keep `shared` deliberately small: stable cross-cutting primitives and utilities only, never capability rules, orchestration, adapters, or transport contracts.
- Treat generated schemas or published API models as contracts; do not share internal domain classes with consumers.
- Make every changed boundary enforceable with Gradle visibility, ArchUnit, contract tests, or static analysis where feasible.

## 3dify-specific guardrails

- 3dify returns evidence-level component results, never bank onboarding or AML decisions.
- Operate 3dify as a managed service hosted by 3dify in Syria. Do not design bank-deployable, on-premise, offline-upgrade, or customer-operated runtime variants unless the user explicitly reverses this product decision.
- Treat tenant isolation as a first-class domain, security, persistence, and testing concern across the shared managed platform. Every bank-facing identity, query, command, object, key, queue item, cache entry, and audit record must be tenant-scoped.
- Only the authenticated bank backend creates sessions, selects profiles, cancels bank-owned workflows, and retrieves full results. Place these entry points under a bank-facing interface package.
- The mobile SDK receives a short-lived token and can access only the capture, resumable-upload, submission, cancellation-by-user, and minimal-status operations authorized for that one session. Never place bank session-creation behavior in an SDK controller.
- Provide a 3dify-hosted operations portal for authorized bank staff. When configured component results are inconclusive, create a tenant-scoped `ReviewCase`; bank reviewers may record `ACCEPTED` or `REJECTED` for the identity evidence.
- Keep automated component conclusions, review-case lifecycle, bank review outcome, and the bank's ultimate customer-onboarding decision as separate immutable concepts. A portal decision never overwrites engine results and is never represented as a 3dify decision.
- Enforce portal SSO/MFA, tenant-scoped RBAC, least-privilege evidence access, optimistic decision concurrency, immutable decision history, and complete reviewer audit trails.
- Preserve `inconclusive` separately from a negative verification conclusion and from a technical failure.
- Treat session, attempt, committed evidence, component execution, and result history as immutable facts where specified by product rules.
- Keep PII out of logs, metrics, traces, event envelopes, webhook summaries, idempotency keys, object keys, and exception text.
- Keep storage and verification engines behind domain ports; do not leak AWS S3 or vendor score semantics into the public API.
- Record model, engine, document-template, rules, threshold, profile, and result-schema versions with every result.
- Treat on-device checks as capture guidance. Server-side processing remains authoritative.

## Review behavior

When reviewing a change, classify findings by violated rule and consequence. Prioritize:

1. domain or contract corruption;
2. transaction, idempotency, or evidence-integrity risk;
3. PII or authorization leakage;
4. dependency-direction violations;
5. test or operability gaps.

Include an exact compliant placement or refactoring direction for each finding. Do not approve code solely because it works at runtime when it violates the architecture.

## When requirements conflict

Stop and surface the conflict when a requested implementation would weaken a non-negotiable boundary, leak a domain model, merge separate use-case transactions, or embed a vendor contract in the domain/API. Offer a DSA-compliant alternative and request an explicit architectural decision if the trade-off is real.
