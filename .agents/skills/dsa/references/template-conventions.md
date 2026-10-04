# domain-service-template conventions

Reference repo: `github.com/traderepublic/domain-service-template` (local clone often at
`~/Documents/domain-service-template`). Base package `com.traderepublic.banking.<name>`. Single Gradle
module, Kotlin + Spring Boot, jOOQ + Flyway, Testcontainers, ArchUnit, MockK, AssertJ, ktlint via Spotless,
TR libraries `duplicate-prevention` and `kotlin-powertools` (structured logging + `@Masked`/`@Mask` PII masking).
**Interfaces may not touch the domain (strict layer isolation).** sepa-processor descends from these conventions.

Use this file as the target when a repo has no stronger conventions of its own.

## Contents
1. Package & file layout
2. Code shapes per layer (copy these)
3. Idempotency with DuplicateCommandPrevention
4. ArchUnit rules
5. Testing conventions
6. Template demo shortcuts NOT to copy

---

## 1. Package & file layout

```
<base>/
├─ BankingDomainServiceApplication.kt                      @SpringBootApplication @ConfigurationPropertiesScan
├─ application/
│  ├─ ApplicationConfig.kt                                 profile constants
│  ├─ ScheduleConfig.kt                                    @EnableScheduling (not in test profile)
│  └─ service/
│     ├─ Command.kt                                        interface Command { val idempotencyKey: String }
│     └─ <model>/
│        ├─ <Model>Response.kt                             app-layer DTO (NOT the domain model), @Masked/@Mask
│        ├─ <Model>ResponseMapper.kt                       domain model → <Model>Response
│        ├─ create/
│        │  ├─ Create<Model>ApplicationService.kt
│        │  ├─ Create<Model>Command.kt                     : Command
│        │  ├─ Create<Model>CommandResult.kt               (response, isDuplicateCommandResult)
│        │  └─ Create<Model>CommandDuplicatePrevention.kt  : DuplicateCommandPreventionStrategy<Create<Model>Command>
│        └─ read/
│           ├─ Read<Model>ApplicationService.kt
│           ├─ FindByIdQuery.kt
│           └─ FindByIdQueryResult.kt
├─ domain/
│  ├─ Entity.kt                                            interface Entity : Validatable { val id: UUID }
│  ├─ validation/Validatable.kt, Validated.kt              Validated<T> runs validate() on construction and unwrap()
│  ├─ transaction/TransactionProvider.kt                   transaction{} / transactionResult{}
│  ├─ duplicate/prevention/DuplicateCommandPrevention.kt   runLambdaIfCommandIsNotDuplicate(cmd) { } : R?
│  └─ <model>/
│     ├─ <Model>.kt                                        data class : Entity, validate() with require()
│     ├─ <Model>Factory.kt                                 @Service, returns Validated<<Model>>
│     ├─ <Model>Repository.kt                              interface
│     └─ <Model>IdByIdempotencyKeyRepository.kt            interface (maps idempotency key → created id)
├─ infrastructure/
│  ├─ messages/{kafka,rabbitmq}/config/...                 broker configuration
│  └─ persistence/postgres/
│     ├─ config/ (JdbcConfiguration @EnableTransactionManagement, JooqDslContextConfiguration, FlywayConfiguration)
│     ├─ transaction/DslContextTransactionProvider.kt      : TransactionProvider
│     ├─ duplicate/prevention/...                          Postgres implementation of DuplicateCommandPrevention
│     ├─ jooq/generated/...                                generated, never hand-edited
│     └─ <model>/
│        ├─ Postgres<Model>Repository.kt                   @Repository : <Model>Repository (DAO + mapper)
│        ├─ <Model>RecordDao.kt                            @Component, the only class using DSLContext for the table
│        └─ <Model>RecordMapper.kt                         record ↔ domain model
└─ interfaces/
   ├─ rest/
   │  ├─ security/WebSecurityConfiguration.kt, WebSecurityAuthorities.kt
   │  ├─ exceptions/ExceptionsControllerAdvice.kt          IllegalArgument → 400, IllegalState → 500
   │  └─ <model>/<usecase>/
   │     ├─ <Usecase><Model>Controller.kt
   │     ├─ <Usecase><Model>RequestObject.kt
   │     ├─ <Usecase><Model>ResponseObject.kt
   │     └─ <Usecase><Model>ResponseObjectMapper.kt        CommandResult/QueryResult → ResponseObject
   ├─ messages/kafka/<model>/{commands,events}/<usecase>/<Usecase><Model>KafkaListener.kt
   ├─ messages/rabbitmq/<model>/{commands,events}/<usecase>/<Usecase><Model>RabbitListener.kt
   └─ schedule/...                                         @Scheduled entry points
```

Message classes (Avro, `*CommandMessage`) come from a separate published library
(`banking-domain-libraries`), not from this repo.

## 2. Code shapes per layer

**Domain model and factory**
```kotlin
data class BankingEntity(override val id: UUID, val name: String, val email: String) : Entity {
    override fun validate() {
        require(name.isNotBlank()) { "{ BankingEntity.name } must not be blank!" }
        require(email.isNotBlank()) { "{ BankingEntity.email } must not be blank!" }
    }
}

@Service
class BankingEntityFactory {
    fun create(name: String, email: String): Validated<BankingEntity> =
        Validated(BankingEntity(id = UUID.randomUUID(), name = name, email = email))
}

interface BankingEntityRepository {
    fun save(validatedBankingEntity: Validated<BankingEntity>): BankingEntity
    fun findById(id: UUID): BankingEntity?
}
```

**Application service (write)**
```kotlin
@Service
class CreateBankingEntityApplicationService(
    private val transactionProvider: TransactionProvider,
    private val bankingEntityFactory: BankingEntityFactory,
    private val bankingEntityRepository: BankingEntityRepository,
    private val bankingEntityResponseMapper: BankingEntityResponseMapper,
    private val duplicateCommandPrevention: DuplicateCommandPrevention<CreateBankingEntityCommand>,
    private val bankingEntityIdByIdempotencyKeyRepository: BankingEntityIdByIdempotencyKeyRepository,
) {
    fun create(command: CreateBankingEntityCommand): CreateBankingEntityCommandResult? =
        transactionProvider.transactionResult {
            duplicateCommandPrevention
                .runLambdaIfCommandIsNotDuplicate(command) { it }
                ?.let { validateAndCreate(it) }
        }

    // Caller (controller) uses this when create() returned null (= duplicate)
    fun findPreviousResultIfDuplicateCommand(command: CreateBankingEntityCommand): CreateBankingEntityCommandResult? { /* id by key → findById → map, isDuplicateCommandResult = true */ }

    private fun validateAndCreate(command: CreateBankingEntityCommand): CreateBankingEntityCommandResult {
        val persisted = bankingEntityRepository.save(bankingEntityFactory.create(command.name, command.email))
        bankingEntityIdByIdempotencyKeyRepository.save(persisted.id, command.idempotencyKey)
        return CreateBankingEntityCommandResult(bankingEntityResponseMapper.map(persisted), isDuplicateCommandResult = false)
    }
}
```

**Application service (read)**: no transaction or idempotency. Returns a `*QueryResult` wrapping app-layer
DTOs, or `null`.

**Infrastructure repository = DAO + mapper**
```kotlin
@Repository
class PostgresBankingEntityRepository(
    private val bankingEntityRecordDao: BankingEntityRecordDao,
    private val bankingEntityRecordMapper: BankingEntityRecordMapper,
) : BankingEntityRepository {
    override fun save(validatedBankingEntity: Validated<BankingEntity>): BankingEntity =
        bankingEntityRecordMapper.map(bankingEntityRecordDao.save(bankingEntityRecordMapper.map(validatedBankingEntity)))
    override fun findById(id: UUID): BankingEntity? = bankingEntityRecordDao.findById(id)?.let(bankingEntityRecordMapper::map)
}

@Component
class BankingEntityRecordDao(private val dslContext: DSLContext) {
    fun save(record: BankingEntityRecord): BankingEntityRecord =
        try {
            dslContext.insertInto(BANKING_ENTITY).set(record).returning().fetchOne()
                ?: throw IllegalStateException("Saving BankingEntityRecord { $record } failed")
        } catch (exception: DataIntegrityViolationException) {
            throw PostgresConstraintViolationException(exception)   // → IllegalArgumentException → HTTP 400
        }
}

@Component
class DslContextTransactionProvider(private val dslContext: DSLContext) : TransactionProvider {
    override fun transaction(lambda: () -> Unit) = dslContext.transaction { _ -> lambda() }
    override fun <T> transactionResult(lambda: () -> T): T = dslContext.transactionResult { _ -> lambda() }
}
```

**Interfaces: controller.** Request object → command → app service → result → response object.
```kotlin
@RestController
class CreateBankingEntityController(
    private val createBankingEntityApplicationService: CreateBankingEntityApplicationService,
    private val createBankingEntityResponseObjectMapper: CreateBankingEntityResponseObjectMapper,
) {
    @PostMapping(CREATE_BANKING_ENTITY_ENDPOINT)
    fun create(@RequestBody requestObject: CreateBankingEntityRequestObject): ResponseEntity<CreateBankingEntityResponseObject> {
        val command = with(requestObject) { CreateBankingEntityCommand(name, email, idempotencyKey) }
        val commandResult = with(createBankingEntityApplicationService) {
            create(command) ?: findPreviousResultIfDuplicateCommand(command)
        } ?: return ResponseEntity.internalServerError().build()
        LOGGER.info(arg(command::class.java.simpleName to command, commandResult::class.java.simpleName to commandResult), "... applied")
        val response = createBankingEntityResponseObjectMapper.map(commandResult)
        return if (response.isDuplicateRequest) ResponseEntity.ok(response) else ResponseEntity.created(URI(...)).body(response)
    }
}
```

**Interfaces: listener.** Deserialise → command → one app-service call. No logic.
```kotlin
@Component
class CreateBankingEntityKafkaListener(private val createBankingEntityApplicationService: CreateBankingEntityApplicationService) {
    @KafkaListener(groupId = GROUP_ID, topics = [TOPIC])
    fun onCreateBankingEntityMessageCommand(genericRecord: GenericRecord) {
        val message = CreateBankingEntityCommandMessage.deserialize(genericRecord)
        createBankingEntityApplicationService.create(with(message) { CreateBankingEntityCommand(name, email, idempotencyKey) })
    }
}
```

## 3. Idempotency with DuplicateCommandPrevention

- Each command implements `Command { val idempotencyKey: String }`.
- Each command type has a `@Component` strategy:
  `class CreateXCommandDuplicatePrevention : DuplicateCommandPreventionStrategy<CreateXCommand>`, with
  `commandName`, `isResponsibleFor(fqcn)`, `hashCommand()` → `HashedCommand(uniqueHash = idempotencyKey,
  hashSpace = commandName, note = toString())`, and `ifDuplicate()` (log).
- **Never rename a `hashSpace`/`commandName` once deployed.** That would reset duplicate detection for
  in-flight commands.
- Postgres table `duplicate_prevention(entry_hash, entry_hash_space, ...)` with a unique constraint.
- Duplicate returns `null` from `runLambdaIfCommandIsNotDuplicate`. The caller fetches the previous result
  through an `*IdByIdempotencyKeyRepository` mapping.

## 4. ArchUnit rules (src/test/.../architecture/)

| Test | Rule |
|---|---|
| `ApplicationServiceLayerArchitectureTest` | `..application.service..` may only be accessed by `..interfaces..` |
| `domain/…ArchitectureTest` | `..domain..` may only be accessed by application service + infrastructure (**not interfaces**) |
| `InfrastructureLayerArchitectureTest` | `..infrastructure..` may not be accessed by any layer |
| `InterfacesLayerArchitectureTest` | `..interfaces..` may not be accessed by any layer |
| `DSLContextArchUnitTest` | no `org.jooq` dependency in interfaces / application service / domain |
| `Junit5ArchUnitTest` | no JUnit 4 `@Test` |
| `MockkArchUnitTest` | no Mockito / Spring Mockito in tests (use MockK / SpringMockK) |

Helper: `ArchitectureTest.assertThatEvaluationResultIsValid(evaluationResult, allowedNumberOfViolations)`.
It allows a non-zero budget, which is useful as a *ratchet* during refactors.

## 5. Testing conventions (from the template's PR checklist)

- **Unit test every unit**, application services included. MockK for collaborators; JUnit 5;
  AssertJ (`assertThat`). Helpers: `TransactionProviderLambdaMocker.mockTransactionResult(tp)` (runs the
  lambda) and `DuplicateCommandPreventionLambdaMocker.mockRunLambdaIfCommandIsNotDuplicate(dcp, isDuplicate)`.
- **Integration test** (`*IntegrationTest`, extends `IntegrationTest`: `@SpringBootTest`, `@Transactional`
  rollback, singleton Testcontainers for Postgres/Rabbit/Kafka, MockMvc with Spring Security) for:
  - every interface-layer entry point, with no asynchronous waiting;
  - every DAO in infrastructure.
- Test data factories in `testing/libraries/testdatafactories/<X>TestDataFactory.create(...)`, with
  random defaults and named overrides.
- Test names: backticked, `` `create - when Command is a duplicate, returns null` ``.
- Observability is part of done: logs (structured `arg(...)`), metrics, traces.

## 6. Template demo shortcuts NOT to copy

The template contains demo code that breaks its own rules. Don't treat it as a pattern:
- `SendDemoEntityApplicationService` injects `RabbitTemplate`/`KafkaTemplate` directly. That puts
  infrastructure in the application layer and is not transactional. Real code uses a domain publisher
  interface implemented by an outbox in infrastructure.
- `CreateDemoEntityCommandResult` wraps the domain model `DemoEntity`. Real results wrap app-layer DTOs.
- `ExperimentEventSender` (domain) exposes an mParticle SDK type. Domain interfaces should use domain types.
- `FeatureFlagsService` lacks the `ApplicationService` suffix and returns SDK types.
