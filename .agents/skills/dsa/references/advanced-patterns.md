# Advanced patterns (the "fancy" techniques worth reusing)

These patterns come from sepa-processor. They are reusable in any DSA service. Each one does one job:
it puts a cross-cutting concern into a **generic contract in the right layer**, so concrete classes stay
tiny and consistent. Copy the idea and its signatures. Don't add a new generic abstraction until there
are about 3 concrete users, and keep generic bases free of business logic.

## Contents
1. Generic ports and bases (repository / DAO / mapper)
2. Self-validating values: `Validatable` + `Validated<T>`
3. State machine framework (`Stateful`, guards, transitions, history)
4. Transaction execution with lock modes (`TransactionManager`)
5. Idempotent service template + per-command strategies
6. Outbox with latency classes, priority predicates and delayed publishing
7. Exhaustive routing over sealed types + config-driven maps
8. Ignorable-transition resolvers (tolerating out-of-order and duplicate events)
9. Small generic utilities: `Executor.WithTimeout`, `DomainRateLimiter<REQ>`, MDC correlation, observed listeners
10. Pitfalls seen in the wild

---

## 1. Generic ports and bases

**Domain (ports)**: generic, typed only with domain types.
```kotlin
interface SimpleRepository<T : Validatable> {
    fun save(validated: Validated<T>): Validated<T>
    fun saveAll(validated: List<Validated<T>>): List<Validated<T>>
    fun existsById(id: UUID): Boolean
    fun findById(id: UUID): T?
    fun getById(id: UUID): T                       // throws when missing
}

interface TransitionalV2<T : Stateful<U>, U : Enum<*>> : SimpleRepository<T> {
    fun transit(transitionV2: Validated<TransitionV2<T, U>>, info: String? = null): Validated<T>
    fun transitMany(transitionPairsV2: Collection<Pair<Validated<TransitionV2<T, U>>, String?>>): Collection<Validated<T>>
}

// A concrete port only adds intent-revealing queries:
interface OutgoingTransferRepository : TransitionalV2<OutgoingTransfer, OutgoingTransfer.State> {
    fun findByExternalId(externalId: UUID): OutgoingTransfer?
}
```

**Infrastructure (bases)**: written once, then extended:
- `SimpleDao<R>` / `SimpleDaoWithId<R>`: generic jOOQ CRUD (`save`, `saveAll`, `findById`, `exists*`,
  `find…ForUpdate`, `updateWithSteps`). ArchUnit forces every `*Dao` to extend one of them.
- `RecordFactory<T, R>` (domain → record) and `EntityMapper<R, T>` (record → domain), one small pair per entity.
- `PostgresSimpleRepository<T, R>` implements `SimpleRepository<T>` from a DAO + factory + mapper.
- `HistoricalTransactionalRepository<T, S, R, H>` implements `TransitionalV2` and writes the entity row
  **and** a history row in the same transaction, so every state change is auditable.

Adding a new entity = model + DAO (extends base) + factory + mapper + a repository that extends the base
and implements the port. Write only the custom queries.

## 2. `Validatable` + `Validated<T>`

```kotlin
interface Validatable { fun validate() }
data class Validated<T : Validatable>(private val value: T) {
    init { value.validate() }
    fun unwrap(): T = value.also { it.validate() }
}
```
Ports accept `Validated<T>`, so it is a compile-time fact that nothing invalid reaches persistence or a
publisher. Factories return `Validated<T>`. The same trick wraps transitions (`Validated<TransitionV2<…>>`), so an
illegal state change cannot even be constructed.

## 3. State machine framework

```kotlin
interface Stateful<T : Enum<*>> : Entity {
    val state: T
    fun validTransitions(): Set<T>                       // per current state, usually a `when (state)`
    fun guards(newState: T): Collection<Guard> = emptySet()   // e.g. NotNullField(valueDate) for INITIATED+
    fun isValidTransition(newState: T) = newState in validTransitions()
    fun inFinalState() = validTransitions().isEmpty()
    override fun validate() { require(guards(state).all { it.isValid() }) { "...failed guards..." } }
}
interface Guard { fun isValid(): Boolean; val reason: String }

fun interface StateMachine<Z : DomainEvent<T>, T : Stateful<U>, U : Enum<*>> {
    fun onEvent(event: Z, entity: T): Validated<TransitionV2<T, U>>?   // null = ignore event
}
```
- The **entity** declares what is legal (`validTransitions` + guards). The **state machine** maps a domain
  event to a target state and returns a validated transition, or `null` to ignore the event. The **app service**
  persists it with `repository.transit(...)`, then publishes.
- `Transition.validate()` throws `InvalidTransitionException`/`InvalidGuardsException` with from/to/entity
  metadata, which gives precise errors in logs.
- Rails send their own state changes back to themselves via the outbox (the `*.state.update` queue →
  `*StateTransitionApplicationService`). Every transition is then its own idempotent, locked, retryable use case.
- Docs can be generated from the enums and transitions (`docs/architecture/generated/`, checked by a test).

## 4. Transaction execution with lock modes

```kotlin
interface TransactionManager {                       // domain port
    fun transaction(callerIdentifier: String? = null, block: () -> Unit): TransactionExecution<Unit>
    fun <T> transactionResult(callerIdentifier: String? = null, block: () -> T): TransactionExecution<T>
}
interface TransactionExecution<T> {
    fun now(): T
    fun lockedOnResource(resourceIdentifier: UUID): T        // per-entity serialisation
    fun lockedOnResourcesUUID(resourceIdentifiers: Set<UUID>): T
    fun lockedOnProcess(process: Process): T                 // global job mutex (enum of stable ids)
}
```
Infra: `JooqTransactionManager` = `dslContext.transactionResult {}` + `pg_advisory_xact_lock(...)`. Ids are
**sorted** before locking to avoid deadlocks, and a Micrometer timer is tagged with `callerIdentifier`. The call site
reads like a sentence: `executeWithDuplicationPrevention(cmd).lockedOnResource(cmd.entityId)`. Concurrency
control lives in the use case and is visible there, not hidden in DAOs.

## 5. Idempotent service template

```kotlin
abstract class AbstractIdempotentService<C : Command, R>(
    private val duplicationPrevention: DuplicateCommandPrevention<C>,
    private val transactionManager: TransactionManager,
) {
    protected fun executeWithDuplicationPrevention(command: C): TransactionExecution<R?> =
        transactionManager.transactionResult(callerIdentifier = this::class.simpleName) {
            withMdc(command.idempotencyKey) { duplicationPrevention.runLambdaIfCommandIsNotDuplicate(command) { idempotentTransactionalExecution(it) } }
        }
    protected abstract fun idempotentTransactionalExecution(command: C): R
}
class XApplicationService(/*...*/) : VoidAbstractIdempotentService<XCommand>(dcp, tm) {
    fun handle(command: XCommand) = executeWithDuplicationPrevention(command).lockedOnResource(command.entityId)
    override fun idempotentTransactionalExecution(command: XCommand) { /* orchestration only */ }
}
@Component class XDuplicationPreventionStrategy : AbstractCommandDuplicationPreventionStrategy<XCommand>(domainSpace = "X", commandClass = XCommand::class)
```
Template method plus strategy: each use case writes only its body, and a test asserts that all `domainSpace`s are unique and never renamed.
Bulk variants (`AbstractBulkedIdempotentService`, `IdempotentProcessor`) and file-pipeline templates
(`AbstractBulkFileCreationService`, `AbstractBulkFileProcessingService` with hook methods) follow the same idea.

## 6. Outbox with latency classes, priority and delay

- **Two outboxes per transport**: `standard` (big batches) and `instant`/`priority` (small batches, own poller).
  This way a 5000-message bulk can't delay a real-time payment.
- `KafkaPriorityAwareEventNotifier<Event, Message>(standardOutbox, priorityOutbox, predicate: DomainEventPriorityPredicate<Event>)`
  lets the domain-level predicate decide routing (e.g. `event.method.isRealTime`). A generic class plus a tiny predicate replaces per-event if/else.
- `KafkaAbstractOutbox<M>` + one `KafkaMessageConverter<M>` bean per topic (topic, key, Avro serialize, JSON for ops).
- **Delayed publishing** through the outbox row's `earliestPublishTime`. Timeouts ("reject if no decision
  in 4.5 s"), "publish settlement at 04:00" and "retry in 10 min" are all just outbox rows, so no schedulers or
  in-memory timers are needed, and the delay survives restarts.
- Payload contracts are interfaces (`*PayloadSchema`) implemented separately by producer (infra) and consumer
  (interfaces) data classes, so both sides evolve against one compiled contract without sharing a class.

## 7. Exhaustive routing over sealed types + config maps

- Routing decisions are an exhaustive `when` over a sealed hierarchy (`OutgoingTransferMethodResolver`:
  `ParticipantDetails` subtype → `TransferMethod`). Adding a subtype is a **compile error** until every
  resolver and mapper handles it. To add a rail, follow the compiler (example commit `225ba1d0c`).
- Method → queue/outbox comes from **yaml** (`queue.rabbit.internal.transfer.outgoing.mapping`, with a `priority` flag),
  loaded into a map. Routing changes are config, and a missing mapping fails fast with a typed exception.

## 8. Ignorable-transition resolvers

`*IgnorableTransitionResolver` (domain) decides which late, duplicate or out-of-order events to drop silently
(e.g. "Executed after Failed", "a second decision after the timeout already rejected"). The state machine
stays strict, and tolerance is explicit, named and unit-tested instead of hidden in try/catch.

## 9. Small generic utilities

- `Executor` (`Default` / `WithTimeout(duration)`), built from config: `executor.execute { ... }` gives a time budget
  (VoP 3 s). Note that `orTimeout` doesn't interrupt the work: the block keeps running.
- `DomainRateLimiter<REQ>.tryConsume(request): RateLimitDecision` is a generic port (Bucket4j/Postgres in infra), reused for VoP-per-IBAN and SCT Inst creation.
- `withMdc(...)`, `CorrelationIdExtractor`, `Correlatable`: correlation ids follow messages through MDC and outbox.
- `ObservedKafkaListenerExecutor.runObserved(record, topic, deserializer, handler)` and
  `GenericRabbitMQProcessor.deserializeAndProcess<T>(raw, onError) {}`: listeners are one call. Metrics, MDC,
  deserialisation errors (→ DLQ) and retries (`RetryableQueueConfiguration<T>`) are handled once.
- `Process` enum: stable, documented lock ids instead of magic numbers.

## 10. Pitfalls (don't copy these parts)

- Lock keys from `hashCode()`: an **enum** hashCode differs per JVM, so it doesn't lock across pods. A UUID hashCode
  is 32 bits and shares a keyspace with `Process` ids. Use stable explicit ints, or hash a stable string.
- `transit` is a blind UPDATE (no `WHERE state = from`). It is only safe when the use case holds `lockedOnResource`.
- Catching a unique-violation exception *inside* the same Postgres transaction: the transaction is already aborted.
- In-process "fake HTTP" ports (`@FakeInternalHTTPCall`) that run another app service in a new transaction from
  inside a transaction: a second connection, and commits that escape rollback.
- Kafka error handler with unbounded backoff: one poison record blocks the partition. Map bad input to failure events.
