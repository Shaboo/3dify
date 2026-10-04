# Source pattern → DSA mapping and transformation recipes

## Contents
1. Recognise the source architecture
2. Construct-by-construct mapping table
3. Recipes (before → after)
   - R1 Fat controller / listener / job
   - R2 God "XService" with `@Transactional` and service-to-service calls
   - R3 ORM entity used as domain model
   - R4 Direct broker / HTTP calls from business code
   - R5 Domain service doing I/O
   - R6 `@Transactional` → `TransactionProvider`
   - R7 Hexagonal / clean architecture renames
   - R8 Missing idempotency
4. Splitting mixed classes
5. Things that must not change during the refactor

---

## 1. Recognise the source architecture

| Pattern | Signals | Distance to DSA |
|---|---|---|
| **Classic Spring layering** | packages `controller/`, `service/`, `repository/`, `model|entity/`, `dto/`. `@Transactional` on services; Spring Data `JpaRepository`; `@Entity` passed everywhere | Large: services must be split into app services and domain services, entities split, ports introduced |
| **Transaction script / fat controller** | Logic, SQL or HTTP calls inside `@RestController`, `@KafkaListener`, `@Scheduled` methods | Large: everything is extracted out of the entry points |
| **God services** | `XService` with 20+ methods, services injecting each other, utils with business rules | Large: split into use cases (one per entry point) plus domain services |
| **Hexagonal (ports & adapters)** | `application/port/in`, `port/out`, `adapter/in`, `adapter/out`, `domain` | Small: mostly a rename. `port/in` use-case interfaces collapse into concrete `*ApplicationService` |
| **Clean architecture** | `usecase/`/`interactor/`, `entity/`, `gateway/`, `presenter/` | Small to medium: interactors become app services, gateways become domain ports plus infra |
| **CQRS / mediator** | `*CommandHandler`, `*QueryHandler`, a dispatcher/bus | Medium: handlers become app-service methods, the bus goes away (interfaces call app services directly) |
| **Package-by-feature, no layers** | `feature/{XController, XService, XRepository, X}` | Medium: keep the feature grouping as **per-model DSA layout** (`<feature>/{application,domain,interfaces,infrastructure}`) |
| **Partially DSA** | some layer packages exist, ArchUnit maybe present with exceptions | Small: finish the job, guided by the repo's own ArchUnit suite |

Run `scripts/inventory.py` first. Its role, flag and current-layer counts tell you which pattern dominates.

## 2. Construct-by-construct mapping

| Source construct | DSA target |
|---|---|
| `@RestController` / `@KafkaListener` / `@RabbitListener` / `@Scheduled` / Quartz `Job` | `interfaces/...`, thin: map, then **one** app-service call, then map back (R1) |
| `@ControllerAdvice` | `interfaces/rest/exceptions` |
| Request/response DTOs, API models | `interfaces/rest/<model>/<usecase>/*RequestObject`/`*ResponseObject` + `*ResponseObjectMapper` |
| Inbound message classes | Generated from the registry schema, or the producer's library. Mapping lives in the listener |
| `XService` public method used by an entry point | One `*ApplicationService` method = one use case (R2) |
| Business rules inside services, controllers or utils | Domain services: `XFactory`, `XValidator`, `XPolicy`/`XResolver`, `XStateMachine`, or invariants in the model's `init {}` |
| Service calling another service | Extract the shared logic into a domain service, or merge the two into one use case. **Never app service → app service** |
| `@Transactional` | `transactionProvider.transaction {}` in the app service. Keep `@EnableTransactionManagement` (R6) |
| JPA `@Entity` / jOOQ record used as model | Pure domain `data class`, plus infra entity/record, plus mapper inside infrastructure (R3) |
| Spring Data `JpaRepository<XEntity, ID>` | Infra detail (`XJpaRepository`) behind domain `XRepository` + `JpaXRepository` adapter. Don't switch ORM during the structural refactor unless asked |
| DAO / `JdbcTemplate` / `DSLContext` code | `infrastructure/persistence/...` behind a domain repository interface |
| `KafkaTemplate` / `RabbitTemplate` / SQS / SNS calls | Domain `XEventPublisher` interface + infra implementation (R4). Converting to an outbox is a separate, explicit step |
| `RestTemplate` / `WebClient` / Feign / Retrofit | Domain `XClient` (named for intent) + infra adapter. Flag mutations made inside DB transactions |
| `@ConfigurationProperties` / `@Value` read by business logic | Bind in infrastructure or application config; pass plain values or a domain config object into domain services |
| `utils/`, `helpers/`, `common/` | Split: business helpers → domain (often value objects); technical helpers → infrastructure or a `shared` package that depends on no layer |
| Domain exceptions | domain. HTTP status mapping lives in interfaces only |
| Feature flags / clock / ID generation | Inject (`Clock`, flags as config) instead of static calls, so domain code stays testable |
| Mapper libraries (MapStruct) | Allowed. The mapper lives in the layer of its *target* type's consumer (e.g. record↔model mapper in infrastructure) |

## 3. Recipes

### R1 Fat controller / listener / job

Before:
```kotlin
@RestController
class PaymentController(private val repo: PaymentJpaRepository, private val kafka: KafkaTemplate<String, Any>) {
    @PostMapping("/payments")
    @Transactional
    fun create(@RequestBody req: CreatePaymentRequest): PaymentEntity {
        if (req.amount <= BigDecimal.ZERO) throw IllegalArgumentException("amount must be positive")   // business rule
        val saved = repo.save(PaymentEntity(id = UUID.randomUUID(), amount = req.amount, status = "NEW"))
        kafka.send("payments", PaymentCreated(saved.id))                                                // non-transactional
        return saved                                                                                    // leaks persistence model
    }
}
```
After:
```kotlin
// interfaces/rest/payment/create/
@RestController
class CreatePaymentController(
    private val createPaymentApplicationService: CreatePaymentApplicationService,
    private val responseMapper: CreatePaymentResponseObjectMapper,
) {
    @PostMapping("/payments")        // path, verb and JSON shape unchanged
    fun create(@RequestBody request: CreatePaymentRequestObject): ResponseEntity<CreatePaymentResponseObject> =
        createPaymentApplicationService.create(CreatePaymentCommand(request.amount, request.idempotencyKey))
            .let { ResponseEntity.ok(responseMapper.map(it)) }
}

// application/service/payment/create/
@Service
class CreatePaymentApplicationService(
    private val transactionProvider: TransactionProvider,
    private val paymentFactory: PaymentFactory,
    private val paymentRepository: PaymentRepository,
    private val paymentEventPublisher: PaymentEventPublisher,
) {
    fun create(command: CreatePaymentCommand): CreatePaymentCommandResult =
        transactionProvider.transactionResult {
            val payment = paymentRepository.save(paymentFactory.create(command.amount))
            paymentEventPublisher.publishCreated(payment)
            CreatePaymentCommandResult(payment.id, payment.amount, payment.status.name)
        }
}

// domain/payment/
data class Payment(val id: Id, val amount: BigDecimal, val status: Status) {
    init { require(amount > BigDecimal.ZERO) { "amount must be positive" } }   // rule moved into the model
    @JvmInline value class Id(val value: UUID)
    enum class Status { NEW }
}
@Service class PaymentFactory { fun create(amount: BigDecimal) = Payment(Payment.Id(UUID.randomUUID()), amount, Payment.Status.NEW) }
interface PaymentRepository { fun save(payment: Payment): Payment }
interface PaymentEventPublisher { fun publishCreated(payment: Payment) }

// infrastructure/persistence/postgres/payment/: JpaPaymentRepository : PaymentRepository (entity ↔ model mapping)
// infrastructure/messages/kafka/payment/: KafkaPaymentEventPublisher : PaymentEventPublisher (same topic + payload as before)
```
Keep the exception→status mapping identical. If the old code turned `IllegalArgumentException` into a 400 via advice, the `require` in `init` still does.

### R2 God service with `@Transactional` and service-to-service calls

Before: `OrderService.placeOrder()` (`@Transactional`) calls `inventoryService.reserve()` (also `@Transactional`) and `pricingService.price()`.

After:
- `PlaceOrderApplicationService.placeOrder(cmd)` owns **one** transaction and orchestrates: load stock via
  `InventoryRepository`, compute price via the `PriceCalculator` domain service, call `stock.reserve(qty)` (domain
  behaviour on the model), save both, publish.
- What was `InventoryService.reserve` becomes domain behaviour (`Stock.reserve()` returning a new `Stock`,
  or a `StockReservationPolicy` domain service) plus repository calls in the app service.
- If `InventoryService.reserve` is *also* an entry point on its own (e.g. a REST endpoint), it gets its
  own `ReserveStockApplicationService` that reuses the same domain service. The two app services share
  domain code, never each other.
- Watch for `REQUIRES_NEW` propagation. It means the old code intentionally committed part of the work
  separately. Model that explicitly: a second use case triggered by an event, or a separate
  `transactionProvider` block with a comment. Don't silently merge it into one transaction.

### R3 ORM entity used as domain model

1. Create a pure `data class X` in `domain/<x>/` with the fields the business needs. Use value classes
   for IDs and `init` validation. No ORM or JSON annotations.
2. Rename the ORM class to `XEntity` (JPA) or keep the jOOQ `XRecord`, and move it to `infrastructure/persistence/...`.
3. Add `XRecordMapper`/`XEntityMapper` (entity ↔ model) in infrastructure.
4. Add a domain `XRepository` with intent-revealing methods (`findOpenByCustomer`, not `findAllByStatusAndCustomerId`).
   Implement it in `PostgresXRepository`/`JpaXRepository`, delegating to the Spring Data repo or DAO.
5. Switch callers one use case at a time. During the transition the adapter can expose both shapes, but
   delete the legacy accessor when its last caller moves.
6. Lazy-loading traps (JPA): the domain model is fully materialised. Make the mapper load what the use
   case needs (fetch joins), or split the aggregate.

### R4 Direct broker / HTTP calls from business code

- Introduce `XEventPublisher` (domain, named after the business event, e.g. `publishPaymentCreated(payment)`).
  Implement it in infrastructure with the **same** topic/exchange, key, headers and payload as today.
  That's a pure refactor with no behaviour change.
- Then, *as a separate, user-approved step*, swap the implementation to the transactional outbox (needs
  a table migration and a relay). This changes delivery timing (async after commit) and guarantees
  (at-least-once), so consumers must dedupe by `idempotencyKey`.
- HTTP: `XClient` domain interface (e.g. `CustomerDirectory.findCustomer(id)`), implemented with the
  existing client in infrastructure. Mutating calls made inside a DB transaction get a finding entry:
  propose moving them outside the transaction plus compensation (SAGA).

### R5 Domain service doing I/O

Before: `FeeCalculator(private val feeRepository: FeeRepository)` loads the fee table itself.
After: the app service loads the data (`feeRepository.findSchedule(...)`) and passes the values in:
`feeCalculator.calculate(amount, schedule)`. The domain service becomes pure and trivially unit-testable.
The I/O becomes visible in the use case.

### R6 `@Transactional` → `TransactionProvider`

- Add `domain/transaction/TransactionProvider` (`transaction {}` / `transactionResult {}`) plus an infra
  implementation:
  - jOOQ: `dslContext.transactionResult {}`; keep `@EnableTransactionManagement` and jOOQ's
    `SpringTransactionProvider`, so Spring test `@Transactional` rollback keeps working;
  - JPA: `TransactionTemplate.execute {}`.
- Replace annotations per use case, not globally. `@Transactional(readOnly = true)` on queries can usually
  be dropped. Keep read consistency if it mattered (repeatable reads).
- Behaviour differences to check:
  - Self-invocation: `@Transactional` on a method called via `this.` was silently non-transactional
    before. Making it transactional now changes behaviour, so note it in Findings.
  - Rollback rules: Spring rolls back on unchecked exceptions only by default. A lambda-based provider
    rolls back on any throwable. Check for checked exceptions used for control flow.

### R7 Hexagonal / clean architecture renames

| From | To |
|---|---|
| `domain/model` | `domain/<model>` |
| `application/port/in/XUseCase` (interface) + `XService` impl | `application/service/<model>/<usecase>/XApplicationService` (concrete, no interface) |
| `application/port/out/LoadXPort`, `SaveXPort` | `domain/<model>/XRepository` (merge load/save ports per aggregate) |
| `adapter/in/web`, `adapter/in/messaging` | `interfaces/rest`, `interfaces/messages/{kafka,rabbitmq}` |
| `adapter/out/persistence`, `adapter/out/http` | `infrastructure/persistence/...`, `infrastructure/rest/...` |
| clean `Interactor` / `UseCase` | `*ApplicationService` |
| clean `Gateway` | domain port + infrastructure adapter |
| clean `Presenter` | interfaces mapper (`*ResponseObjectMapper`) |

Use `git mv` and IDE-safe renames. Fix imports with the compiler, not by hand-editing hundreds of files with regex.

### R8 Missing idempotency

Adding duplicate-command prevention changes behaviour (duplicates stop having effects), so it needs
user approval and usually a migration.
- `DuplicateCommandPrevention` + one strategy per command (stable `commandName`/`domainSpace`, never renamed).

Until approved, keep the commands shaped for it (`idempotencyKey` on the command) but don't enforce.

## 4. Splitting mixed classes

For each flagged class (inventory flags `fat-entry-point`, `infra-in-application`, `domain-service-does-io`, `impure-domain`):
1. List its methods and, for each, mark the lines as: *protocol*, *orchestration*, *business rule*, *I/O*.
2. Protocol stays in interfaces. Orchestration moves to the app service. Business rules move to the
   domain. I/O moves behind a port, into infrastructure.
3. Move code verbatim first (green), then improve names and shapes (green). Don't redesign while moving.
4. Static/companion helpers used everywhere: move to the layer of their content. Leave a deprecated
   forwarding alias only if many callers can't all be changed in one slice.

## 5. Must not change during the refactor (contract freeze)

Record these in the plan's *Contract freeze* section before touching code, and verify them at the end:
- HTTP paths, verbs, status codes, JSON field names/casing/nullability, error body format, auth rules.
- Topic/queue/exchange/routing-key names, message schemas, headers, keys/partitioning, consumer group IDs.
- DB schema (no renames during structural refactor), Flyway history (never edit applied migrations).
- Idempotency hash spaces / `stableName`s / `domainSpace`s, Quartz job and trigger names (orphan rows!),
  ShedLock names, metric names and tags (dashboards and alerts depend on them), log keys used by alerts.
- Spring bean names referenced from config (`@Qualifier`, `@ConditionalOnBean`), `@ConfigurationProperties` prefixes.
- Feature flags and their defaults.
