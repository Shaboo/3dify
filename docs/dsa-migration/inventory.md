# DSA inventory: /Users/shaboo/Documents/3dify

_Heuristic classification, confirm each row by reading the class._

## Summary

- Files scanned: 47
- Current layer: infrastructure=20, interfaces=14, application=9, shared=3, domain=1
- Suggested layer: infrastructure=18, interfaces=16, application=9, domain=3, root=1
- Role: entry-point=12, use-case-orchestration=8, persistence-adapter=8, domain-service?=5, integration-adapter=5, configuration=4, model/value/util=2, composition-root=1, api-or-message-dto=1, http-error-mapping=1
- Flags: @Transactional→TransactionProvider=3, many-types-in-file=3, fat-entry-point=2, misplaced? infrastructure→interfaces=1

## Entry points (12) → one use case each (start the use-case list here)

| Entry point | Annotations | Injects | Flags |
|---|---|---|---|
| `OutboxPublisher` (src/main/kotlin/com/omni3d/infrastructure/messaging/OutboxPublisher.kt) | Component Scheduled | ObjectMapper, OutboxRepository | fat-entry-point: OutboxRepository; misplaced? infrastructure→interfaces |
| `TaskWorker` (src/main/kotlin/com/omni3d/interfaces/messaging/TaskWorker.kt) | Component RabbitListener | JobService, ObjectMapper, RunPodClient | fat-entry-point: RunPodClient |
| `AdminController` (src/main/kotlin/com/omni3d/interfaces/rest/AdminController.kt) | RestController | PlanService |  |
| `ApiKeyController` (src/main/kotlin/com/omni3d/interfaces/rest/ApiKeyController.kt) | RestController | ApiKeyService |  |
| `AuthController` (src/main/kotlin/com/omni3d/interfaces/rest/AuthController.kt) | RestController | UserService |  |
| `GenerateController` (src/main/kotlin/com/omni3d/interfaces/rest/GenerateController.kt) | RestController | JobHistoryService, JobService, StorageService |  |
| `JobController` (src/main/kotlin/com/omni3d/interfaces/rest/JobController.kt) | RestController | JobHistoryService, JobService |  |
| `PublicPlanController` (src/main/kotlin/com/omni3d/interfaces/rest/PublicPlanController.kt) | RestController | PlanService |  |
| `SubscriptionController` (src/main/kotlin/com/omni3d/interfaces/rest/SubscriptionController.kt) | RestController | SubscriptionService |  |
| `WebhookController` (src/main/kotlin/com/omni3d/interfaces/rest/WebhookController.kt) | RestController | WebhookService |  |
| `ProviderWebhookController` (src/main/kotlin/com/omni3d/interfaces/webhook/ProviderWebhookController.kt) | RestController | JobService, WebhookService |  |
| `StripeWebhookController` (src/main/kotlin/com/omni3d/interfaces/webhook/StripeWebhookController.kt) | RestController | SubscriptionService |  |

## Flagged classes (6)

| Class | Current | Suggested | Role | Flags |
|---|---|---|---|---|
| `JobService` (src/main/kotlin/com/omni3d/application/service/JobService.kt) | application | application | use-case-orchestration | @Transactional→TransactionProvider |
| `OutboxService` (src/main/kotlin/com/omni3d/application/service/OutboxService.kt) | application | application | use-case-orchestration | @Transactional→TransactionProvider |
| `SubscriptionService` (src/main/kotlin/com/omni3d/application/service/SubscriptionService.kt) | application | application | use-case-orchestration | @Transactional→TransactionProvider |
| `UserEntity` (src/main/kotlin/com/omni3d/domain/entities.kt) | domain | domain (or shared if purely technical) | model/value/util | many-types-in-file: 11 |
| `RegisterRequest` (src/main/kotlin/com/omni3d/interfaces/rest/dto/Dtos.kt) | interfaces | interfaces (or infrastructure if outbound payload) | api-or-message-dto | many-types-in-file: 19 |
| `ApiException` (src/main/kotlin/com/omni3d/shared/exception/ApiException.kt) | shared | domain (or shared if purely technical) | model/value/util | many-types-in-file: 5 |

## All classes

| File | Type | Current | Suggested | Role | Tech | Injects | Flags |
|---|---|---|---|---|---|---|---|
| src/main/kotlin/com/omni3d/application/service/ApiKeyService.kt | class `ApiKeyService` | application | application | use-case-orchestration |  | ApiKeyRepository, PlanRepository |  |
| src/main/kotlin/com/omni3d/application/service/JobHistoryService.kt | class `JobHistoryService` | application | application | use-case-orchestration |  | JobHistoryRepository |  |
| src/main/kotlin/com/omni3d/application/service/JobService.kt | class `JobService` | application | application | use-case-orchestration | spring-tx | JobHistoryService, JobRepository, OutboxService | @Transactional→TransactionProvider |
| src/main/kotlin/com/omni3d/application/service/OutboxService.kt | class `OutboxService` | application | application | use-case-orchestration | jackson,spring-tx | ObjectMapper, OutboxRepository | @Transactional→TransactionProvider |
| src/main/kotlin/com/omni3d/application/service/PlanService.kt | class `PlanService` | application | application | use-case-orchestration |  | PlanRepository |  |
| src/main/kotlin/com/omni3d/application/service/RateLimiterService.kt | class `RateLimiterService` | application | application (keep; weak signal: domain) | domain-service? |  |  |  |
| src/main/kotlin/com/omni3d/application/service/SubscriptionService.kt | class `SubscriptionService` | application | application | use-case-orchestration | spring-tx,spring-web | ApiKeyRepository, PlanRepository, SubscriptionRepository | @Transactional→TransactionProvider |
| src/main/kotlin/com/omni3d/application/service/UserService.kt | class `UserService` | application | application | use-case-orchestration |  | JwtService, UserRepository |  |
| src/main/kotlin/com/omni3d/application/service/WebhookService.kt | class `WebhookService` | application | application | use-case-orchestration |  | WebhookRepository |  |
| src/main/kotlin/com/omni3d/domain/entities.kt | class `UserEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  | many-types-in-file: 11 |
| src/main/kotlin/com/omni3d/infrastructure/Omni3dApplication.kt | class `Omni3dApplication` | infrastructure | root package (no layer) | composition-root |  |  |  |
| src/main/kotlin/com/omni3d/infrastructure/billing/StripeConfig.kt | class `StripeConfig` | infrastructure | infrastructure (tech config) or owning layer | configuration |  |  |  |
| src/main/kotlin/com/omni3d/infrastructure/config/FlywayConfig.kt | class `FlywayConfig` | infrastructure | infrastructure (tech config) or owning layer | configuration |  |  |  |
| src/main/kotlin/com/omni3d/infrastructure/config/JacksonConfig.kt | class `JacksonConfig` | infrastructure | infrastructure (tech config) or owning layer | configuration | jackson |  |  |
| src/main/kotlin/com/omni3d/infrastructure/config/RabbitConfig.kt | class `RabbitConfig` | infrastructure | infrastructure | integration-adapter | rabbit |  |  |
| src/main/kotlin/com/omni3d/infrastructure/config/S3Config.kt | class `S3Config` | infrastructure | infrastructure | integration-adapter | aws |  |  |
| src/main/kotlin/com/omni3d/infrastructure/config/SecurityConfig.kt | class `SecurityConfig` | infrastructure | infrastructure | configuration | spring-web |  |  |
| src/main/kotlin/com/omni3d/infrastructure/messaging/OutboxPublisher.kt | class `OutboxPublisher` | infrastructure | interfaces | entry-point | jackson | ObjectMapper, OutboxRepository | fat-entry-point: OutboxRepository; misplaced? infrastructure→interfaces |
| src/main/kotlin/com/omni3d/infrastructure/messaging/TaskProducer.kt | class `TaskProducer` | infrastructure | infrastructure | integration-adapter | jackson,rabbit | ObjectMapper, RabbitTemplate |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/ApiKeyRepository.kt | class `ApiKeyRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/JobHistoryRepository.kt | class `JobHistoryRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/JobRepository.kt | class `JobRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/OutboxRepository.kt | class `OutboxRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/PlanRepository.kt | class `PlanRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/SubscriptionRepository.kt | class `SubscriptionRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/UserRepository.kt | class `UserRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/persistence/WebhookRepository.kt | class `WebhookRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/omni3d/infrastructure/provider/runpod/RunPodClient.kt | class `RunPodClient` | infrastructure | infrastructure | integration-adapter | http-client,spring-web |  |  |
| src/main/kotlin/com/omni3d/infrastructure/security/JwtService.kt | class `JwtService` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? |  |  |  |
| src/main/kotlin/com/omni3d/infrastructure/storage/StorageService.kt | class `StorageService` | infrastructure | infrastructure | integration-adapter | aws | S3Client |  |
| src/main/kotlin/com/omni3d/interfaces/messaging/TaskWorker.kt | class `TaskWorker` | interfaces | interfaces | entry-point | http-client,jackson,rabbit,spring-web | JobService, ObjectMapper, RunPodClient | fat-entry-point: RunPodClient |
| src/main/kotlin/com/omni3d/interfaces/rest/AdminController.kt | class `AdminController` | interfaces | interfaces | entry-point | spring-web | PlanService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/ApiKeyController.kt | class `ApiKeyController` | interfaces | interfaces | entry-point | spring-web | ApiKeyService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/AuthController.kt | class `AuthController` | interfaces | interfaces | entry-point | spring-web | UserService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/GenerateController.kt | class `GenerateController` | interfaces | interfaces | entry-point | spring-web | JobHistoryService, JobService, StorageService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/JobController.kt | class `JobController` | interfaces | interfaces | entry-point | spring-web | JobHistoryService, JobService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/PublicPlanController.kt | class `PublicPlanController` | interfaces | interfaces | entry-point | spring-web | PlanService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/SubscriptionController.kt | class `SubscriptionController` | interfaces | interfaces | entry-point | spring-web | SubscriptionService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/WebhookController.kt | class `WebhookController` | interfaces | interfaces | entry-point | spring-web | WebhookService |  |
| src/main/kotlin/com/omni3d/interfaces/rest/dto/Dtos.kt | class `RegisterRequest` | interfaces | interfaces (or infrastructure if outbound payload) | api-or-message-dto | jackson |  | many-types-in-file: 19 |
| src/main/kotlin/com/omni3d/interfaces/rest/filter/ApiKeyAuthFilter.kt | class `ApiKeyAuthFilter` | interfaces | interfaces (keep; weak signal: domain) | domain-service? | spring-web | ApiKeyService, RateLimiterService, SubscriptionRepository |  |
| src/main/kotlin/com/omni3d/interfaces/rest/filter/JwtAuthFilter.kt | class `JwtAuthFilter` | interfaces | interfaces (keep; weak signal: domain) | domain-service? | spring-web | JwtService, UserRepository |  |
| src/main/kotlin/com/omni3d/interfaces/webhook/ProviderWebhookController.kt | class `ProviderWebhookController` | interfaces | interfaces | entry-point | http-client,spring-web | JobService, WebhookService |  |
| src/main/kotlin/com/omni3d/interfaces/webhook/StripeWebhookController.kt | class `StripeWebhookController` | interfaces | interfaces | entry-point | spring-web | SubscriptionService |  |
| src/main/kotlin/com/omni3d/shared/exception/ApiException.kt | class `ApiException` | shared | domain (or shared if purely technical) | model/value/util | spring-web |  | many-types-in-file: 5 |
| src/main/kotlin/com/omni3d/shared/exception/GlobalExceptionHandler.kt | class `GlobalExceptionHandler` | shared | interfaces | http-error-mapping | jackson,spring-web | ObjectMapper |  |
| src/main/kotlin/com/omni3d/shared/metrics/AppMetrics.kt | class `AppMetrics` | shared | domain | domain-service? | micrometer |  |  |
