# DSA review checklist

Use this for a self-review before finishing, or for a PR review. Go through the diff file by file and
decide which layer each touched class belongs to. Then apply that layer's checks. Cite file:line for
every finding, and say which rule it breaks and why that matters. A bare "DSA violation" helps nobody.

## Placement
- [ ] Every new class is in the package of the layer whose job it does (see the "Where does this code go" table in SKILL.md).
- [ ] The layout matches the repo's existing pattern (flat vs per-model) and its naming suffixes.
- [ ] No new "misc" buckets (`utils`, `helpers`, `common`, `manager`) that sidestep the layering. If shared code is needed, it goes into the repo's sanctioned shared/common package, which depends on no layer.

## Interfaces (controllers, listeners, jobs)
- [ ] Exactly one application-service call per entry-point invocation (no use-case composition).
- [ ] No repository, factory, domain service, publisher, `DSLContext` or `*Dao` injected.
- [ ] No domain model returned or published. Interface-layer request/response/payload types are used. Domain enums are OK.
- [ ] No business `if`/`when`. Only mapping, protocol handling (status codes, ack/retry/DLQ), auth and logging.
- [ ] Idempotency key taken from the message or header and put on the command.
- [ ] Listener errors follow the repo's retry/DLQ convention (do not swallow exceptions silently).

## Application services
- [ ] Name ends in `ApplicationService`. Each public method is one use case.
- [ ] Does not inject or call another `*ApplicationService`.
- [ ] Does not inject infrastructure classes (`*Dao`, `DSLContext`, `RabbitTemplate`, `KafkaTemplate`, `RestTemplate`/`WebClient`, `S3Client`, concrete `Postgres*Repository`). Only domain interfaces and domain services.
- [ ] Write use cases run inside the repo's transaction abstraction and its idempotency mechanism.
- [ ] Messages are published via outbox-backed domain publishers, inside the transaction.
- [ ] No external HTTP *mutation* inside the DB transaction. If unavoidable, compensation is in place.
- [ ] No business decisions inline. Branching on domain state lives in a domain service, state machine or model.
- [ ] Locking/concurrency uses the repo's mechanism (advisory lock, version CAS), not ad-hoc `FOR UPDATE` in a DAO.

## Domain
- [ ] No imports of `..interfaces..`, `..application..` or `..infrastructure..`.
- [ ] No `org.jooq`, Jackson/JAXB/Avro/JPA annotations, Spring Web/AMQP/Kafka types, AWS SDK, `@Transactional`, `@Value`, `@ConfigurationProperties`.
- [ ] Domain services do not inject `*Repository`, `*Publisher`/`*Notifier`, `*Client` or `TransactionProvider`.
- [ ] Invariants are enforced at construction (`init { require(...) }` or the `Validated` wrapper).
- [ ] New state transitions are added to the state machine / `validTransitions()`, not patched in callers.
- [ ] Infrastructure ports are named for intent (`TransferRepository`, `TransferEventPublisher`), not technology (`TransferKafkaSender`).

## Infrastructure
- [ ] Implements a domain interface. It is not called directly by any other layer.
- [ ] jOOQ records, Avro classes and HTTP DTOs are mapped to and from domain types inside infrastructure.
- [ ] DAOs follow the repo's base class and naming (`*Dao`, `*RecordDao`, `SimpleDao`, ...).
- [ ] New queues, topics or tables are declared where the repo expects (yaml, `definitions.json`, infra repo, Flyway migration).

## Tests
- [ ] Unit tests with MockK for each unit, plus integration tests for every entry point and DAO.
- [ ] The test file name puts it in the right test task. Some repos classify by filename (`*IntegrationTest`, `*Listener*Test`).
- [ ] The ArchUnit suite passes. No new allow-list entries or escape-hatch annotations without a written justification.

## Severity guide
- **Blocker**: money/state correctness risk (non-transactional publish, missing idempotency, use case split across two transactions), or ArchUnit failure.
- **Major**: wrong layer for logic (business rules in a controller or app service, I/O in a domain service), domain model leaking into an API.
- **Minor**: naming or package placement, missing mapper or DTO where the repo convention expects one.
