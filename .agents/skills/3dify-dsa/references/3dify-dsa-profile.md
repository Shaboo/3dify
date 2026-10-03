# 3dify Domain Service Architecture Profile

## Table of contents

1. [Purpose](#purpose)
2. [Adopted architecture](#adopted-architecture)
3. [Dependency model](#dependency-model)
4. [Layer responsibilities](#layer-responsibilities)
5. [Repository and module shape](#repository-and-module-shape)
6. [Use-case and transaction rules](#use-case-and-transaction-rules)
7. [Contracts, messages, and events](#contracts-messages-and-events)
8. [3dify modeling rules](#3dify-modeling-rules)
9. [Testing and enforcement](#testing-and-enforcement)
10. [Placement examples](#placement-examples)
11. [Definition of done](#definition-of-done)

## Purpose

This profile adapts the February 2026 Domain Service Architecture blueprint supplied by the 3dify founder. It is the default for all 3dify production code. It combines tactical DDD with a layer-first, single-project modular monolith.

The profile resolves the blueprint's selectable interface-enforcement approaches as follows:

- Use one Gradle/Spring Boot project. Express boundaries with layer-first packages and ArchUnit rather than physical Spring/Gradle modules.
- Keep interface adapters independent of the domain layer. An interface maps API/message models to application commands and maps application results back to contracts.
- Permit a domain enum in a public contract only after an explicit review determines it is genuinely stable. Prefer contract-owned enums by default.
- Keep the 3dify domain free of Spring annotations. Wire domain services at the composition boundary. This is a 3dify-specific strengthening of domain purity.

## Adopted architecture

Each domain model or cohesive capability owns four implementation layers:

| Layer | Purpose |
|---|---|
| `application.service` | Expose and orchestrate complete use cases |
| `domain` | Express domain state, behavior, rules, events, and required ports |
| `interfaces` | Adapt HTTP, message, scheduled, CLI, or SDK-facing invocations into use cases |
| `infrastructure` | Implement domain ports with specific technologies |

The normal invocation chain is:

```text
outside world
  -> interfaces
  -> application service
  -> domain models/services and domain ports
  -> infrastructure implementation of domain ports
```

Infrastructure depends inward on domain abstractions. Domain code never reaches outward.

## Dependency model

### Allowed compile-time dependencies

| Source | May depend on |
|---|---|
| `interfaces` contracts | Contract primitives and deliberately selected schema libraries |
| `domain` | Kotlin/JDK and approved domain-only utilities |
| `application.service` | `domain` |
| `interfaces` | Its own contracts, `application.service` |
| `infrastructure` | `domain`, technology libraries |
| Composition root | All required modules for wiring only |

### Forbidden dependencies

- `domain` -> Spring, Jackson, jOOQ/JPA, HTTP, messaging, storage SDK, ML runtime, interfaces, application, or infrastructure.
- `application.service` -> interfaces or infrastructure implementation classes.
- `interfaces` -> domain, infrastructure, repositories, clients, event publishers, or another interface adapter.
- `infrastructure` -> interfaces or application-service orchestration.
- Public contract package -> application implementation, infrastructure, or domain model.
- One `ApplicationService` -> another `ApplicationService`.

Dependency injection does not legalize a forbidden dependency. A domain service that injects a repository interface still hides I/O in the domain and violates this profile.

## Layer responsibilities

### Application service

An application service describes a use case offered by 3dify. It may:

- accept a use-case-specific command;
- load domain state through domain repository ports;
- call domain entities, factories, validators, policies, and domain services;
- coordinate multiple domain models inside one use case;
- select a logical transaction and rollback/compensation strategy;
- apply idempotency, authorization context, logging, and metrics as application concerns;
- save state and publish events through domain ports;
- return a use-case-specific result.

It must not:

- contain verification or business decisions;
- implement document, liveness, face, session-transition, or retention rules as orchestration branches when those are domain rules;
- call another application service;
- depend on an infrastructure implementation;
- expose a domain entity as its external contract;
- keep a database transaction open over an external mutation.

Use the `ApplicationService` suffix. Prefer one public method per use case. A class may contain closely related methods only when each still owns a complete, unambiguous use case.

Command and result types live with the use case in `application.service`. Shape them around the use case rather than mirroring an aggregate.

### Domain

The domain contains:

- entities and aggregate roots;
- immutable value objects and typed identifiers;
- domain services, policies, factories, and validators;
- domain events;
- repository, publisher, clock/randomness, evidence-store, verification-engine, cryptography, and client ports when the domain requires those capabilities.

Domain objects enforce invariants on construction and state transitions. Prefer behavior-rich methods over public mutable state. Preserve original evidence-related values separately from normalized forms.

Domain services are pure business collaborators. They receive needed state and values as arguments. They do not perform I/O and do not inject ports whose implementations perform I/O.

Domain ports remain technology-neutral. Name them after required capability, such as `EvidenceStore`, `VerificationSessionRepository`, `DocumentVerificationEngine`, or `DomainEventPublisher`; never `S3EvidenceStore` or `PostgresSessionRepository` in the domain.

Do not put transport validation, row mapping, JSON annotations, persistence annotations, Spring stereotypes, vendor result classes, or deployment configuration in domain code.

### Interfaces

Interface adapters are entry points:

- REST controllers;
- message listeners;
- scheduled invocations;
- administrative CLI handlers;
- SDK session endpoints.

An interface adapter may:

1. authenticate or receive an already authenticated principal;
2. validate transport-level shape;
3. map a contract request to one application command;
4. invoke one application-service method representing the entire use case;
5. map the application result to a contract response.

It must not:

- expose domain models;
- call domain services, repositories, publishers, or clients;
- compose a use case by calling multiple application services;
- make a document or biometric conclusion;
- open a business transaction;
- depend on infrastructure implementation details.

Request, response, and error models are contract-owned and live below the owning `interfaces` capability.

### Infrastructure

Infrastructure implements domain ports and owns technology-specific code:

- PostgreSQL/jOOQ repositories and row mappers;
- transaction-provider implementations;
- S3-compatible evidence storage;
- encryption/HSM/KMS adapters;
- OCR, liveness, face, and document-engine adapters;
- webhook HTTP delivery;
- outbox persistence and relay;
- message-broker integration;
- technical configuration, health indicators, and migrations.

Infrastructure maps vendor/technology types to domain types at the boundary. It must not decide the use-case flow or contain business rules merely because the data is available there.

## Repository and package shape

Begin as one Gradle/Spring Boot modular monolith. Under `com.3dify`, use exactly these package roots:

```text
com/3dify/
├─ application/service/
│  ├─ session/
│  ├─ evidence/
│  └─ <capability>/
├─ infrastructure/
│  ├─ configuration/
│  ├─ persistence/jooq/
│  └─ <capability>/
├─ domain/
│  ├─ session/
│  ├─ evidence/
│  └─ <capability>/
├─ interfaces/
│  ├─ bank/
│  ├─ sdk/
│  └─ portal/
└─ shared/
```

Capabilities are semantic modules below each layer root, not Spring or Gradle modules. Put Spring Boot startup and wiring in `infrastructure.configuration`. Extract a deployable only after an explicit ADR backed by measured scaling, security-isolation, availability, runtime, or release-cadence needs.

`shared` may be referenced by every layer but contains only stable cross-cutting primitives and utilities. It must not contain capability rules, orchestration, adapters, persistence, or public contracts.

## Use-case and transaction rules

### One application service owns the lifecycle

For `CreateVerificationSession`, the owning application service handles idempotency, selects the immutable profile, creates the aggregate through domain behavior, persists it, writes the event through the publisher port, and returns a purpose-built result.

Do not implement this as controller -> profile application service -> session application service -> token application service. The owning application service may use the necessary domain models and ports directly.

### Logical versus database transactions

An application-service method owns one logical transaction: the full success, failure, retry, and compensation strategy for a use case. It may contain multiple short database transactions and asynchronous steps.

- Keep local state changes that must be atomic in one database transaction.
- Do not invoke external HTTP mutations or long-running ML work inside that transaction.
- Persist work to be performed and resume asynchronously.
- Use explicit compensation for an external mutation that cannot participate in the local transaction.
- Make retry behavior safe and visible.

### Transactional outbox

When a state change and message must be atomic, the domain publisher port writes an outbox record inside the same database transaction. A relay delivers later.

- Assume at-least-once delivery.
- Give every message an event/message ID, idempotency key, correlation ID, causation ID, occurred-at timestamp, tenant ID, schema version, and opaque aggregate reference as applicable.
- Consumers deduplicate durably.
- Never include PII, images, embeddings, document numbers, or free-form error text in the event envelope.

## Contracts, messages, and events

### Public API contracts

Public request/response models live under the owning `interfaces` capability and are verified against the OpenAPI contract.

The API model is optimized for consumers and versioning. It is not a copy of the aggregate. Map explicitly:

```text
API request -> application command -> domain behavior
domain/use-case outcome -> application result -> API response
```

### Message contracts

Producer-owned message schemas belong under the producing capability's `interfaces` contract package and in the schema registry or versioned schema catalogue. Consumers should generate classes from schemas when possible. Do not distribute internal domain event classes as integration contracts.

Distinguish:

- **Domain event:** an internal business fact expressed in domain language.
- **Integration event:** a stable external or cross-module contract derived from committed state.
- **Webhook event:** a minimal notification telling the bank which canonical resource/version to retrieve.

## 3dify modeling rules

### Product boundary

3dify verifies evidence components. The bank owns account-opening and AML decisions. No domain or API model may contain `customerApproved`, `amlDecision`, or equivalent 3dify-owned decisions.

3dify is a managed service operated by 3dify in Syrian infrastructure. Banks consume the server API, mobile SDK, and operations portal; they do not install, upgrade, administer, or operate a 3dify runtime. Do not introduce bank-deployable packaging or per-bank forks.

The initial runtime is multi-tenant. Tenant context is mandatory in authentication, authorization, repositories, object storage, queues, caches, metrics, rate limits, encryption-key selection, audit, and support tooling. Use defense in depth such as PostgreSQL row-level security where compatible with the persistence design, per-tenant key separation, tenant-aware worker claims, and automated cross-tenant negative tests. A later dedicated cell may still be 3dify-managed; it is not an on-prem bank deployment.

The bank API and SDK API are different trust surfaces:

- The authenticated bank backend creates sessions, selects profiles, retrieves full results, and performs bank-authorized administration.
- The SDK token is scoped to one session and only permits capture, resumable upload, submission, user cancellation, and minimal processing status as configured.
- A bank-server use case belongs in `interfaces/rest/bank`; a token-scoped mobile entry point belongs in `interfaces/rest/sdk`.
- Never accept the bank's durable API credential or a profile-selection decision from the SDK.

The operations portal is a third trust surface:

- 3dify hosts and operates it as part of the managed service.
- Bank users authenticate through a tenant-bound identity and SSO/MFA policy.
- Portal roles include reviewer, supervisor, auditor, and tenant administrator as required; roles do not cross tenant boundaries.
- Evidence access is purpose-bound, least-privilege, time-limited, and audited. Raw downloads are disabled by default.

### Review case domain

Create a `ReviewCase` when a versioned verification profile requires human resolution for one or more inconclusive component results.

- A review case references immutable session, attempt, result, evidence, profile, model, template, and threshold versions.
- The review lifecycle is separate from the verification-session lifecycle. Prefer `OPEN`, `ASSIGNED`, `IN_REVIEW`, `RESOLVED`, and `CANCELLED` as distinct states, subject to the final domain model.
- A resolved outcome is `ACCEPTED` or `REJECTED` and means the bank reviewer accepted or rejected the submitted identity evidence for the bank's process. It is not an account-opening or AML decision.
- A decision records reviewer identity, tenant, required reason code, optional bounded note, decided-at time, and decision version.
- A decision never changes automated component results. Corrections create a superseding decision/version with an audit link and, where configured, supervisor authorization.
- Use optimistic concurrency or an explicit assignment/lease to prevent two reviewers from silently deciding the same case.
- Publish minimal `review_case.opened` and `review_case.resolved` notifications; the canonical portal or bank API resource contains the details.
- Keep review rules, eligibility, transition validity, and decision immutability in `review/domain`; keep queue queries, evidence loading, assignment orchestration, and event publication in application services.

### Outcome taxonomy

Keep these axes independent:

- execution state: pending, running, completed, unavailable, error;
- evidence conclusion: confirmed/not-confirmed/inconclusive, or match/no-match/inconclusive;
- session lifecycle: created, capturing, uploading, submitted, processing, completed, expired, cancelled, failed;
- retry strategy: immediate, delayed, recapture, new attempt/session, none.

A negative evidence conclusion is not a system failure. A connectivity failure is not an identity conclusion.

### Historical facts

- A selected verification-profile version never changes for an existing session.
- Committed evidence is immutable.
- Submission freezes the attempt's evidence set.
- Component execution records preserve input references and all engine/model/rules/template/threshold versions.
- A new attempt or reprocessing evaluation creates a new result version; it never overwrites history.

### Storage and engine ports

Public/domain contracts express behavior, not vendors. `EvidenceStore` exposes bounded streams, immutable commit, checksum, deletion, retention/hold behavior, and health/capacity as needed. It does not expose buckets or provider-specific URLs.

Verification-engine ports return 3dify-owned typed results and provenance. Vendor scores and error classes are mapped inside infrastructure. Changing a vendor must not change public API meaning.

## Testing and enforcement

### ArchUnit baseline

Add architecture tests that discover layer packages at any nesting depth.

Enforce at least:

- domain depends on no 3dify layer and no forbidden technology package;
- application depends only on domain and approved application utilities;
- interfaces depend on application and API contract, not domain or infrastructure;
- infrastructure depends on domain, not interfaces;
- classes suffixed `ApplicationService` do not depend on other classes suffixed `ApplicationService`;
- controllers/listeners/schedulers do not depend on repository, publisher, client, or engine ports;
- domain classes have no Spring, Jackson, persistence, jOOQ, AWS, broker, or model-runtime annotations/imports;
- public contract packages do not depend on domain, application implementation, or infrastructure;
- no production class exists outside the five approved `com.3dify` package roots;
- PostgreSQL repositories use generated jOOQ tables and `DSLContext`, with no handwritten JDBC persistence or row mappers.

Use wildcard package matching so the rules apply to every domain-model group.

### Test ownership

| Concern | Primary test |
|---|---|
| Entity/value-object invariant | Pure domain unit test |
| Domain service/policy | Pure domain unit/property test |
| Use-case orchestration | Application-service unit test with port fakes |
| Repository/object-store/engine mapping | Infrastructure integration or contract test |
| HTTP/message mapping | Interface contract test |
| Layer dependencies | ArchUnit/Gradle test |
| Idempotency/outbox/recovery | Database integration and end-to-end test |
| Public compatibility | OpenAPI/schema compatibility test |

Prefer fakes for domain ports in application tests. Use Testcontainers for real infrastructure mappings. Do not mock domain behavior to make orchestration tests pass.

### Security and privacy enforcement

Add tests or scanners that detect prohibited data in:

- structured logs;
- metric labels;
- trace attributes;
- event and webhook fixtures;
- exception messages;
- object keys;
- idempotency keys.

Authorization tests must prove cross-tenant and cross-session references return no existence information.

Portal and review tests must also prove:

- reviewers cannot access another tenant's queue, case, evidence, decision, user, or aggregate counts;
- only allowed roles can assign, inspect, decide, supersede, or audit a case;
- two concurrent decisions yield one committed outcome and one explicit conflict;
- automated results remain byte-for-byte historically unchanged after review;
- review decisions and evidence views produce immutable audit entries;
- browser responses disable caching of evidence and sensitive review content;
- object URLs are short-lived, viewer-scoped, and unusable outside the authorized case.

## Placement examples

| Code or decision | Placement |
|---|---|
| `VerificationSession`, state-transition rules | `domain/session` |
| `CreateVerificationSessionApplicationService` | `application/service/session` |
| `CreateVerificationSessionCommand/Result` | beside the application service |
| HTTP request/response | `interfaces/<surface>/<capability>/contract` |
| REST controller and mapper | `session/interfaces/rest` |
| `VerificationSessionRepository` port | `domain/session` |
| jOOQ repository and generated-record mapper | `infrastructure/session/persistence/postgres` |
| `EvidenceStore` capability port | `domain/evidence` |
| S3-compatible SDK adapter | `infrastructure/evidence/storage/s3compatible` |
| Whether an attempt may be submitted | `domain/session` |
| Loading evidence and invoking engines | owning `application.service` |
| Liveness threshold policy | `domain/biometric` or versioned domain configuration |
| Vendor liveness call and response mapper | `infrastructure/biometric/engine/<vendor>` |
| `ReviewCase`, eligibility and decision transitions | `domain/review` |
| Open/assign/decide review use cases | `application/service/review` |
| Portal review API and contract mapper | `interfaces/portal/review` |
| Review persistence and queue query adapter | `review/infrastructure/persistence/postgres` |
| Portal SSO/OIDC adapter | `review/infrastructure/security` or shared access infrastructure |
| Operations portal UI | `apps/operations-portal` |
| Create session and issue initial SDK token | `session/interfaces/rest/bank` |
| SDK upload/submit/status endpoint | owning capability's `interfaces/rest/sdk` |
| Token signing implementation | `session/infrastructure/security` |
| Webhook retry scheduling | `notification/application.service` plus infrastructure scheduler |
| HTTP webhook delivery | `notification/infrastructure/webhook` |
| Domain event creation | domain factory/entity behavior |
| Outbox storage and relay | infrastructure behind the domain publisher port |

## Definition of done

Before declaring a 3dify change complete, verify:

- [ ] The owning domain capability and use case are named.
- [ ] Business rules live in domain code and have pure tests.
- [ ] One application-service method owns the whole use case.
- [ ] No application service calls another application service.
- [ ] I/O is visible in the application-service orchestration.
- [ ] External mutation or long-running processing is outside the database transaction.
- [ ] Interface adapters invoke one use case and expose contract models only.
- [ ] Infrastructure implements technology-neutral domain ports.
- [ ] Public contracts contain no domain or vendor types.
- [ ] Idempotency and at-least-once delivery behavior are tested where applicable.
- [ ] No PII enters logs, events, webhooks, object keys, or diagnostics.
- [ ] Historical evidence and result invariants are preserved.
- [ ] Managed-service tenant isolation is preserved and cross-tenant negative tests cover every changed access path.
- [ ] Any review behavior preserves automated results, records bank actor provenance, and distinguishes identity-evidence review from onboarding/AML decisions.
- [ ] Unit, integration, contract, architecture, and end-to-end tests are updated as applicable.
- [ ] Any exception has an explicit rationale and ADR candidate.
