---
name: dsa
description: Trade Republic's Domain Service Architecture (DSA), the 4-layer pattern (interfaces → application service → domain ← infrastructure) used by TR Kotlin/Spring Boot backend services such as sepa-processor and services built from domain-service-template. Use this skill whenever you write, place, move, review or explain backend code in a DSA repo. That includes adding a use case or *ApplicationService, a REST controller, a Rabbit/Kafka listener, a Quartz or @Scheduled job, a repository/DAO, an outbox publisher or notifier, a domain model, factory, validator or state machine; deciding which package or layer a class belongs in; fixing ArchUnit layer violations; checking idempotency or transaction boundaries; or reviewing a PR "for architecture". Use it even if the user never says "DSA" but is working in such a repo. For migrating a whole non-DSA codebase, use the dsa-refactor skill instead.
---

# Domain Service Architecture (DSA)

DSA is TR's convention for structuring backend services, adapted from DDD. Its goal is that any engineer
(or agent) can open any service and immediately know where things are. So the most valuable thing
you can do is **put code where a TR engineer expects it, shaped like its neighbours**.

```
outside world ─▶ interfaces ─▶ application service ─▶ domain ◀─ infrastructure
                 (REST, Rabbit/  (one use case per      (models, rules,     (jOOQ repos, outbox
                  Kafka, jobs)    method; tx boundary,   state machines,     publishers, HTTP clients,
                                  idempotency)           ports/interfaces)   tech config)
```
Dependencies point inward only. Nobody depends on `interfaces` or `infrastructure`; Spring wires
infrastructure implementations into the domain interfaces.

## Step 1: Learn the repo's dialect (core + adapt)

The core rules are the floor. The repo's own conventions decide the details. Check, in this order:

| Signal in the repo | Conventions | Read next |
|---|---|---|
| `src/main/kotlin/com/traderepublic/banking/sepa/` (this repo) | sepa-processor dialect of domain-service-template | `references/sepa-processor.md` |
| one Gradle module with `application/service`, `domain`, `infrastructure`, `interfaces` packages and ArchUnit tests | domain-service-template | `references/template-conventions.md` |
| none of the above (greenfield or unstructured) | domain-service-template conventions | `references/template-conventions.md` |

Then, before writing anything:
1. Read the repo's ArchUnit tests (`src/test/**/architecture/**`).
   They are the executable spec: layer identifiers, naming rules, escape-hatch annotations, budgets.
2. Find the **closest existing neighbour**: same subdomain, same kind of class (listener, app service, DAO...).
   Mirror its package depth, naming suffixes, base classes, mappers and test style.
   Consistency with the repo beats the template.
3. If the repo's convention contradicts a core rule (e.g. it lets interfaces touch domain types via an
   annotation), follow the repo for consistency, avoid spreading the deviation, and mention it to the user.

`references/core-rules.md` has every rule with its rationale. Read it when a rule is contested or unclear.

## Step 2: The rules that matter most

1. **Interfaces are thin.** An entry point maps input → command/query, calls **exactly one**
   application-service method, and maps the result → response/ack. No repositories, domain services,
   publishers or business `if`s. Never return or publish a domain model (domain enums are fine).
2. **One application-service method = one use case = one logical transaction.** It orchestrates
   domain services and ports, owns the transaction plus idempotency, and contains no business rules and no
   technology code. **It never calls another application service.** Share logic by extracting a domain service.
3. **The domain is pure.** Models validate themselves (`init { require(...) }` / `Validated`), business
   decisions live in domain services and state machines, and I/O is only *declared* as interfaces
   (`*Repository`, `*Publisher`/`*Notifier`, `*Client`, `TransactionProvider`/`TransactionManager`).
   Domain services do not inject those I/O interfaces. The app service loads data and passes values in.
   No Jackson/JAXB/jOOQ/JPA annotations, no `@Transactional`/`@Value`/`@ConfigurationProperties` in new domain code.
4. **Infrastructure implements domain interfaces**, and only it touches jOOQ, brokers, HTTP and AWS.
   Records and DTOs are mapped to domain types inside infrastructure.
5. **Every command is idempotent** (it carries an `idempotencyKey`; duplicates have no side effects), and
   **every published message goes through the transactional outbox** inside the use-case transaction.
   No `RabbitTemplate`/`KafkaTemplate` calls from app or domain code. No external HTTP mutation inside a DB
   transaction (use compensation instead).

## Step 3: Where does this code go?

| You are writing... | Layer / package | Typical name |
|---|---|---|
| HTTP endpoint | `interfaces/rest/<model>/<usecase>/` | `<Usecase><Model>Controller`, `*RequestObject`/`*ResponseObject` + mapper |
| Rabbit / Kafka consumer | `interfaces/{rabbit,messages/kafka}/<...>/` | `<Usecase><Model>{Rabbit,Kafka}Listener` |
| Cron / Quartz trigger | `interfaces/scheduled/<...>/` | `<Name>Job` + `<Name>JobConfig` |
| HTTP error mapping | `interfaces/rest/exceptions/` | `*ControllerAdvice` |
| Use-case orchestration | `application/service/<model>/<usecase>/` | `<Usecase><Model>ApplicationService` |
| Command / query / result | `application/service/<model>/<usecase>/` | `*Command`, `*Query`, `*CommandResult` |
| Business rule, decision, calculation | `domain/<model>/` | `*Factory`, `*Validator`, `*Policy`, `*Resolver`, `*Calculator` |
| Lifecycle / state transitions | `domain/<model>/` | `*StateMachine` + `validTransitions()` |
| Entity, value object, domain event | `domain/<model>/` | `<Model>`, `<Model>.Id`, `<Model>CreatedEvent` |
| Need to persist / publish / call out | `domain/<model>/` (interface) | `<Model>Repository`, `<Model>EventPublisher`/`*Notifier`, `<Thing>Client` |
| ...and its implementation | `infrastructure/{persistence/postgres,outbox,rest,...}/<model>/` | `Postgres<Model>Repository` (+ `*RecordDao`, `*RecordMapper`), `Rabbit*Notifier`, `*RestClient` |
| Broker / DB / HTTP client config | `infrastructure/.../config/` | `*Configuration` |
| Message contract shared by producer & consumer | external library / schema registry (sepa-processor: `shared/message/*PayloadSchema`) | |

## Step 4: Recipes (exact code in `references/template-conventions.md`)

**Write use case (command)**
1. Domain: the model plus invariants, a factory or domain service for the decision, port interfaces if new I/O is needed.
2. Application: `*Command` (with `idempotencyKey`) + `*ApplicationService`:
   transaction { idempotency { factory/domain service → repository.save → publisher.publish } } → result DTO.
3. Infrastructure: implement the new ports (repository + DAO + mapper, outbox publisher, client).
4. Interfaces: entry point + request/payload mapping + response mapping. Exactly one app-service call.
5. Wiring that lives outside code: migration, queue/topic declaration, config keys, feature flag.
6. Tests in the repo's style (Step 5).

**New entity / repository / lifecycle**: reuse the generic bases in `references/advanced-patterns.md`
(`SimpleRepository<T>`, `Validated<T>`, `Stateful` + `StateMachine`, `SimpleDao`, `AbstractIdempotentService`)
instead of writing the plumbing by hand.

**Read use case (query)**: entry point → `Read*ApplicationService.find*(query)` → repository →
mapped result. No transaction or idempotency machinery is needed.

**React to an event/message**: a listener maps the payload to a command and calls one app service. If the reaction
needs several steps, they all belong to that one app-service method.

**State transition**: add it to the model's `validTransitions()`/state machine (domain). The app service
loads the entity (with the repo's lock if concurrent updates are possible), asks the state machine,
persists, and publishes. Never patch state in a controller or DAO.

**Call another system**: domain `*Client` port named for intent, infrastructure adapter. Reads may happen
in the use case. Mutations go outside the DB transaction, with compensation if the transaction fails.

## Step 5: Verify before you say "done"

- Compile, then run the **ArchUnit suite** and the tests for what you touched.
  In sepa-processor see `references/sepa-processor.md` §7 for test-task naming and `-x test`.
- Self-review the diff with `references/review-checklist.md`. ArchUnit misses several important rules,
  so check them by hand: domain services doing I/O, business logic in app services or controllers, two
  app-service calls in one entry point, non-outbox publishing, and (in sepa-processor) all outgoing-direction layer rules.
- Tests: MockK unit tests per unit (domain, app services, mappers), plus integration tests for every entry point and DAO.
- If you added an escape-hatch annotation, a tolerance or an allow-list entry, say so explicitly and why.

## Reviewing code for DSA

Walk the diff with `references/review-checklist.md`. For each finding, give file:line, the rule, the
concrete risk (e.g. "publish isn't transactional, so a rollback still emits the event"), and the
DSA-shaped fix. Distinguish repo-sanctioned deviations (annotated escape hatches) from new violations.

## References
- `references/core-rules.md`: the full rule set with rationale and edge cases.
- `references/template-conventions.md`: domain-service-template layout, code shapes, idempotency, ArchUnit rules, tests.
- `references/advanced-patterns.md`: the "fancy" reusable techniques (generic ports/bases, state machines, lock modes, priority/delayed outbox, sealed routing, ...). Use them when adding entities, repositories, services, flows.
- `references/sepa-processor.md`: this repo's frameworks, recipes, real ArchUnit coverage, deviations not to copy.
- `references/review-checklist.md`: layer-by-layer review checklist and severity guide.
- Upstream: `github.com/traderepublic/domain-service-architecture`, `.../domain-service-template`.
