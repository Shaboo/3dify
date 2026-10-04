# DSA core rules

These are the rules every Trade Republic DSA service shares, whatever template it came from.
Source of truth: `github.com/traderepublic/domain-service-architecture`; reference implementation `domain-service-template`.
DSA borrows from Domain-Driven Design (DDD). The rules exist to reduce *engineer time to productive*:
every service should look the same, so you only have to learn a new domain, not a new structure.

## Contents
1. Layers and dependency direction
2. Interfaces layer
3. Application service layer
4. Domain layer
5. Infrastructure layer
6. Transactions, idempotency, messaging
7. Package layout options
8. Edge cases (enums, message classes, shared/common code)
9. How the rules are enforced

---

## 1. Layers and dependency direction

| DSA layer | DDD name | Contains | May depend on |
|---|---|---|---|
| `interfaces` | presentation | entry points: REST controllers, Rabbit/Kafka listeners, gRPC/GraphQL handlers, scheduled jobs | application service |
| `application.service` (or `application`) | application | `*ApplicationService` classes, one per use case or a few use cases per class; commands, queries, results | domain |
| `domain` | domain | models, value objects, domain services (factories, validators, policies, state machines), domain events, and the **interfaces** the domain needs (repositories, publishers, clients, `TransactionProvider`) | nothing in other layers |
| `infrastructure` | infrastructure | implementations of the domain interfaces (jOOQ repositories, outbox publishers, HTTP clients), technology config | domain |

```
outside world ──▶ interfaces ──▶ application service ──▶ domain ◀── infrastructure (implements domain interfaces)
```

Read it as concentric circles: the domain is the core, application services wrap it, and interfaces
(input) and infrastructure (output) sit on the outer ring. Dependencies only point inward.
Nothing depends on `interfaces` or `infrastructure`; Spring dependency injection wires the
infrastructure implementations into the domain interfaces at runtime.

Each layer has its own kind of "service":
- **Application Service**: exposes and composes a use case.
- **Domain Service**: holds domain logic, i.e. business decisions.
- **Infrastructure Service**: integrates one specific technology.
- The interfaces layer has no services, only entry points.

## 2. Interfaces layer

Purpose: translate the outside world's protocol into a call to one application service method, then
translate the result back.

Requirements (the *why* matters, because reviewers argue about these):
1. **Never expose domain models in the API surface.** HTTP bodies, published payloads, GraphQL types
   etc. are interface-layer types (`*ResponseObject`, `*RequestObject`, API models). Reason: adding a field
   to a domain model must not silently change the contract other teams depend on.
2. **Do not invoke domain services directly.** No repositories, factories or publishers from a
   controller or listener. If an entry point starts composing those, it is doing an application
   service's job, and the transaction boundary and idempotency get lost.
3. **Do not compose a use case from two application-service calls.** If you need A and then B
   atomically, write a new application-service method that does both inside one boundary.

Enums from the domain may be reused in request and response objects, because they are behaviour-free value types.

Enforcement: **strict layer isolation**. Interfaces have *zero* access to the domain (ArchUnit-enforced).
   Application services return their own result DTOs. Watch out: if those DTOs
  just mirror the domain model field for field, the decoupling is fake. Shape them by the use case.

Group code by technology: `interfaces/rest`, `interfaces/messages/{kafka,rabbitmq}` (some repos use
`interfaces/rabbit`), `interfaces/scheduled`.

A listener or job should contain:
- deserialisation / mapping into a command or query,
- a single application-service call,
- protocol-level concerns (ack, retry, DLQ, auth, HTTP status mapping, structured logging).

It should contain no business `if`s.

## 3. Application service layer

An Application Service is an **orchestrator**, i.e. glue code. Each public method implements **one
complete use case** and owns its **logical transaction boundary**, including the failure and
rollback strategy.

Allowed in an application service (application-level logic):
- composing domain services, repositories, publishers and clients to realise the use case;
- transaction demarcation (`transactionProvider.transaction { ... }`);
- idempotency / duplicate-command prevention;
- cross-cutting concerns: logging, metrics, tracing;
- mapping domain results into result DTOs.

Not allowed:
- **Domain logic**: business rules, business decisions, domain validation. Ask: *"Is this a business
  rule or decision?"* If yes, it belongs in a domain service, a model's `init {}`, or a state machine.
- **Infrastructure logic**: SQL, HTTP, serialisation, broker APIs (`RabbitTemplate`, `KafkaTemplate`,
  `DSLContext`). Call a domain interface instead.
- **Calling another application service.** Each method is a self-contained boundary. If one calls
  another, nobody can tell which one owns the use case lifecycle. Extract the shared part into a domain
  service, or write a new use-case method. Depending on *several repositories or aggregates* is
  fine: application services orchestrate use cases, they do not partition domain models.

Name them with the `ApplicationService` suffix. They expose the expensive I/O, which makes the cost of
a use case visible at a glance.

**Transaction rules**
- DB writes, plus message publishing through the **transactional outbox**, happen inside one DB
  transaction.
- **External HTTP mutations must not run inside a DB transaction.** Use SAGA-like behaviour: if the DB
  transaction rolls back, issue compensating calls to the external services already invoked.
- One application-service method owns the whole lifecycle. Do not open nested or competing
  transactions in lower layers.

Shape of a write use case:

```kotlin
@Service
class CreateXApplicationService(
    private val transactionProvider: TransactionProvider,   // domain interface
    private val duplicateCommandPrevention: DuplicateCommandPrevention<CreateXCommand>,
    private val factory: XFactory,                          // domain service
    private val repository: XRepository,                    // domain interface
    private val eventPublisher: XEventPublisher,            // domain interface, outbox-backed
) {
    fun create(command: CreateXCommand) =
        transactionProvider.transaction {
            duplicateCommandPrevention.runLambdaIfCommandIsNotDuplicate(command) {   // duplicate → null, no side effects
                val x = repository.save(factory.create(command.a, command.b))
                eventPublisher.publish(XCreated(x))          // written to the outbox in the same tx
                x
            }
        }
}
```

## 4. Domain layer

Contains:
1. **Domain models**: entities and value objects as plain (data) classes. Use `@JvmInline value class`
   for identifiers and validated primitives, and `init { require(...) }` for invariants, so an
   invalid model cannot be constructed.
2. **Domain logic (domain services)**: factories, validators, policies/resolvers, state machines,
   calculators. This is where business decisions and conditional business logic live.
3. **Domain events.**
4. **Interfaces the infrastructure must implement**: `*Repository`, `*EventPublisher`/`*Publisher`/
   `*Notifier`, `*Client`, `TransactionProvider`, clocks/ID providers if abstracted.

The domain must not:
- depend on `interfaces`, `application.service` or `infrastructure` code;
- **inject its own I/O interfaces into domain services.** A factory or validator should not take a
  `Repository`, `EventPublisher` or `Client`, even though those interfaces live in the domain package.
  Their implementations do I/O, and composing I/O is the application service's job. This rule is the
  one most often broken; ArchUnit usually can't see it, so reviewers must. The fix: the application
  service loads the data and passes values into the domain service;
- carry serialisation or DB-mapping annotations (Jackson, JAXB, jOOQ, JPA, Avro);
- contain technology-specific code (SQL, HTTP, AWS SDK, broker APIs).

`@Service`/`@Component` on domain services is accepted in both templates, purely to get dependency
injection. Spring *behaviour* annotations are not: `@Transactional`, `@Value`,
`@ConfigurationProperties`, `@Scheduled`, `@RabbitListener`. Pass configuration values in as
parameters, or as a plain domain config object built by infrastructure.

## 5. Infrastructure layer

Implements the domain's technology-agnostic interfaces with technology-specific classes. This is
dependency inversion: infrastructure depends on the domain, never the reverse.
- Persistence: `Postgres<Model>Repository : <Model>Repository`, using jOOQ (directly, or via
  `*Dao`/`*RecordDao` plus `*RecordMapper`). Records never leave infrastructure. Map to domain models at the
  boundary.
- Messaging: outbox-backed publishers implementing the domain publisher interfaces; broker config.
- HTTP clients implementing domain `*Client` interfaces.
- `TransactionProvider` implementation (for example `DslContextTransactionProvider` → `dsl.transactionResult {}`).
- Technology configuration classes (`JdbcConfiguration`, `KafkaConfiguration`, ...).
- **Only infrastructure may depend on `org.jooq`.**

## 6. Transactions, idempotency, messaging

- **Every command is idempotent.** Commands carry an `idempotencyKey`; a duplicate must not repeat
  side effects and should return the original result (or a "duplicate" marker).
- **Publishing is transactional** via the outbox: `publish()` inserts into an outbox table inside the
  caller's transaction, and a relay ships to the broker later. This gives *at-least-once* delivery,
  so every message carries an `idempotencyKey` and every consumer deduplicates.
- Never call `RabbitTemplate.send`/`KafkaTemplate.send` from application or domain code. It is not
  transactional, so a rollback would still leave the message published.
- Prefer optimistic concurrency (a version column with compare-and-swap) over `SELECT ... FOR UPDATE`
  where the repo allows it.

## 7. Package layout options

Both are valid; ArchUnit layer identifiers are wildcards (`..domain..`, `..application..`), so nesting depth doesn't matter.

**Flat (layer-first)**: one set of layers, sub-packages per model or use case inside each:
```
<base>/application/service/<model>/<usecase>/CreateXApplicationService.kt, CreateXCommand.kt, CreateXCommandResult.kt
<base>/domain/<model>/X.kt, XFactory.kt, XRepository.kt, events/...
<base>/interfaces/{rest,messages/kafka,messages/rabbitmq,scheduled}/<model>/...
<base>/infrastructure/{persistence/postgres,messages,...}/<model>/...
```

**Per domain model (model-first)**, recommended for larger services:
```
<base>/<model>/{application(.service),domain,interfaces,infrastructure}/...
```

Never mix the two inside one service. Follow whatever the repo already does.

## 8. Edge cases

- **Message classes.**
  - Producer side: messages live in a published library plus a
    schema registered in the schema registry (Glue).
  - Consumer side: prefer classes generated from the registry schema (glue-downloader plugin). Depend on
    the producer's library only when it carries runtime validation the schema can't express.
  - The interfaces layer (consumers) and infrastructure layer (publishers) must not depend on each
    other, which is why messages are external.
- **Shared/common code.** Some repos have a `shared`/`common` package (utils, value types, message
  DTOs). It must not depend on any layer. Check the repo's ArchUnit tests for who may use it.
- **Enums.** May cross layers (see §2).
- **Read-only queries.** Still go interfaces → application service (`Read*ApplicationService` /
  `find*`) → repository. Skip the transaction and idempotency machinery.

## 9. Enforcement

ArchUnit tests encode the layer rules. Typical set:
- application service only accessed by interfaces;
- domain only by application service and infrastructure;
- infrastructure and interfaces accessed by nobody;
- no `org.jooq` outside infrastructure.

Repos add their own (naming suffixes, MockK only, JUnit 5 only, no app-service → app-service calls,
...).
**The repo's ArchUnit suite is the executable spec. Run it after every structural change.**
