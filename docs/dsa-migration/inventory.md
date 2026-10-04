# DSA inventory: /Users/shaboo/Documents/3dify

_Heuristic classification, confirm each row by reading the class._

## Summary

- Files scanned: 152
- Current layer: application=67, domain=38, infrastructure=25, interfaces=18, shared=3, none=1
- Suggested layer: application=67, domain=41, infrastructure=25, interfaces=18, root=1
- Role: model/value/util=37, use-case-orchestration=27, command/query/result=24, domain-service?=16, port=14, entry-point=12, persistence-adapter=8, integration-adapter=6, configuration=5, composition-root=1, api-or-message-dto=1, http-error-mapping=1
- Flags: several-app-services (check no single handler composes them)=7, many-types-in-file=4

## Entry points (12) → one use case each (start the use-case list here)

| Entry point | Annotations | Injects | Flags |
|---|---|---|---|
| `TaskWorker` (src/main/kotlin/com/thridify/interfaces/messaging/TaskWorker.kt) | Component RabbitListener | DispatchGenerationTaskApplicationService, ObjectMapper |  |
| `AdminController` (src/main/kotlin/com/thridify/interfaces/rest/AdminController.kt) | RestController | CreatePlanApplicationService, DeactivatePlanApplicationService, ListPlansApplicationService, UpdatePlanApplicationService | several-app-services (check no single handler composes them): CreatePlanApplicationService,DeactivatePlanApplicationService,ListPlansApplicationService,UpdatePlanApplicationService |
| `ApiKeyController` (src/main/kotlin/com/thridify/interfaces/rest/ApiKeyController.kt) | RestController | CreateApiKeyApplicationService, ListApiKeysApplicationService, RevokeApiKeyApplicationService | several-app-services (check no single handler composes them): CreateApiKeyApplicationService,ListApiKeysApplicationService,RevokeApiKeyApplicationService |
| `AuthController` (src/main/kotlin/com/thridify/interfaces/rest/AuthController.kt) | RestController | LoginUserApplicationService, RegisterUserApplicationService | several-app-services (check no single handler composes them): LoginUserApplicationService,RegisterUserApplicationService |
| `GenerateController` (src/main/kotlin/com/thridify/interfaces/rest/GenerateController.kt) | RestController | GenerateModelApplicationService, GetJobApplicationService, GetJobHistoryApplicationService, ListApiKeyJobsApplicationService | several-app-services (check no single handler composes them): GenerateModelApplicationService,GetJobApplicationService,GetJobHistoryApplicationService,ListApiKeyJobsApplicationService |
| `JobController` (src/main/kotlin/com/thridify/interfaces/rest/JobController.kt) | RestController | GetJobHistoryApplicationService, ListUserJobsApplicationService | several-app-services (check no single handler composes them): GetJobHistoryApplicationService,ListUserJobsApplicationService |
| `PublicPlanController` (src/main/kotlin/com/thridify/interfaces/rest/PublicPlanController.kt) | RestController | ListActivePlansApplicationService |  |
| `SubscriptionController` (src/main/kotlin/com/thridify/interfaces/rest/SubscriptionController.kt) | RestController | CreateBillingPortalApplicationService, CreateCheckoutSessionApplicationService, GetSubscriptionStatusApplicationService | several-app-services (check no single handler composes them): CreateBillingPortalApplicationService,CreateCheckoutSessionApplicationService,GetSubscriptionStatusApplicationService |
| `WebhookController` (src/main/kotlin/com/thridify/interfaces/rest/WebhookController.kt) | RestController | DeleteWebhookApplicationService, GetWebhookApplicationService, SetWebhookApplicationService | several-app-services (check no single handler composes them): DeleteWebhookApplicationService,GetWebhookApplicationService,SetWebhookApplicationService |
| `OutboxPublisher` (src/main/kotlin/com/thridify/interfaces/scheduled/OutboxPublisher.kt) | Component Scheduled | PublishPendingGenerationTasksApplicationService |  |
| `ProviderWebhookController` (src/main/kotlin/com/thridify/interfaces/webhook/ProviderWebhookController.kt) | RestController | HandleGenerationCallbackApplicationService |  |
| `StripeWebhookController` (src/main/kotlin/com/thridify/interfaces/webhook/StripeWebhookController.kt) | RestController | HandleStripeWebhookApplicationService |  |

## Flagged classes (4)

| Class | Current | Suggested | Role | Flags |
|---|---|---|---|---|
| `BillingEvent` (src/main/kotlin/com/thridify/domain/billing/BillingEvent.kt) | domain | domain (or shared if purely technical) | model/value/util | many-types-in-file: 6 |
| `GenerationPolicy` (src/main/kotlin/com/thridify/domain/generation/GenerationPolicy.kt) | domain | domain | domain-service? | many-types-in-file: 5 |
| `RegisterRequest` (src/main/kotlin/com/thridify/interfaces/rest/dto/Dtos.kt) | interfaces | interfaces (or infrastructure if outbound payload) | api-or-message-dto | many-types-in-file: 19 |
| `ApiException` (src/main/kotlin/com/thridify/shared/exception/ApiException.kt) | shared | domain (or shared if purely technical) | model/value/util | many-types-in-file: 5 |

## All classes

| File | Type | Current | Suggested | Role | Tech | Injects | Flags |
|---|---|---|---|---|---|---|---|
| src/main/kotlin/com/thridify/3difyApplication.kt | file `3difyApplication` | none | root package (no layer) | composition-root |  |  |  |
| src/main/kotlin/com/thridify/application/service/access/authorize/AuthorizeApiRequestApplicationService.kt | class `AuthorizeApiRequestApplicationService` | application | application | use-case-orchestration |  | ApiKeyRepository, SubscriptionRepository |  |
| src/main/kotlin/com/thridify/application/service/access/authorize/AuthorizeApiRequestCommand.kt | class `AuthorizeApiRequestCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/apikey/create/ApiKeyCreatedResult.kt | class `ApiKeyCreatedResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/apikey/create/CreateApiKeyApplicationService.kt | class `CreateApiKeyApplicationService` | application | application | use-case-orchestration |  | ApiKeyRepository, PlanRepository |  |
| src/main/kotlin/com/thridify/application/service/apikey/create/CreateApiKeyCommand.kt | class `CreateApiKeyCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/apikey/list/ApiKeyResult.kt | class `ApiKeyResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/apikey/list/ListApiKeysApplicationService.kt | class `ListApiKeysApplicationService` | application | application | use-case-orchestration |  | ApiKeyRepository |  |
| src/main/kotlin/com/thridify/application/service/apikey/list/ListApiKeysQuery.kt | class `ListApiKeysQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/apikey/revoke/RevokeApiKeyApplicationService.kt | class `RevokeApiKeyApplicationService` | application | application | use-case-orchestration |  | ApiKeyRepository |  |
| src/main/kotlin/com/thridify/application/service/apikey/revoke/RevokeApiKeyCommand.kt | class `RevokeApiKeyCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/generation/callback/GenerationCallbackResult.kt | class `GenerationCallbackResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/generation/callback/HandleGenerationCallbackApplicationService.kt | class `HandleGenerationCallbackApplicationService` | application | application | use-case-orchestration |  | CustomerWebhookClient, JobHistoryRepository, JobRepository, WebhookRepository |  |
| src/main/kotlin/com/thridify/application/service/generation/callback/HandleGenerationCallbackCommand.kt | class `HandleGenerationCallbackCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/generation/dispatch/DispatchGenerationTaskApplicationService.kt | class `DispatchGenerationTaskApplicationService` | application | application | use-case-orchestration |  | GenerationProviderClient, JobHistoryRepository, JobRepository |  |
| src/main/kotlin/com/thridify/application/service/generation/dispatch/DispatchGenerationTaskCommand.kt | class `DispatchGenerationTaskCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/generation/publish/PublishPendingGenerationTasksApplicationService.kt | class `PublishPendingGenerationTasksApplicationService` | application | application | use-case-orchestration |  | OutboxRepository |  |
| src/main/kotlin/com/thridify/application/service/generation/submit/GenerateModelApplicationService.kt | class `GenerateModelApplicationService` | application | application | use-case-orchestration |  | GenerationTaskPublisher, JobHistoryRepository, JobRepository, TransactionProvider |  |
| src/main/kotlin/com/thridify/application/service/generation/submit/GenerateModelCommand.kt | class `GenerateModelCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/generation/submit/GenerateResult.kt | class `GenerateResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/generation/submit/GenerationImage(val.kt | class `GenerationImage` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/identity/AuthResult.kt | class `AuthResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/identity/authenticate/AuthenticateJwtApplicationService.kt | class `AuthenticateJwtApplicationService` | application | application | use-case-orchestration |  | TokenClient, UserRepository |  |
| src/main/kotlin/com/thridify/application/service/identity/authenticate/AuthenticateJwtQuery.kt | class `AuthenticateJwtQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/identity/login/LoginUserApplicationService.kt | class `LoginUserApplicationService` | application | application | use-case-orchestration |  | TokenClient, UserRepository |  |
| src/main/kotlin/com/thridify/application/service/identity/login/LoginUserCommand.kt | class `LoginUserCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/identity/register/RegisterUserApplicationService.kt | class `RegisterUserApplicationService` | application | application | use-case-orchestration |  | TokenClient, UserRepository |  |
| src/main/kotlin/com/thridify/application/service/identity/register/RegisterUserCommand.kt | class `RegisterUserCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/JobResult.kt | class `JobResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/JobResultMapper.kt | file `JobResultMapper` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/getjob/GetJobApplicationService.kt | class `GetJobApplicationService` | application | application | use-case-orchestration |  | JobRepository |  |
| src/main/kotlin/com/thridify/application/service/job/getjob/GetJobQuery.kt | class `GetJobQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/history/GetJobHistoryApplicationService.kt | class `GetJobHistoryApplicationService` | application | application | use-case-orchestration |  | JobHistoryRepository |  |
| src/main/kotlin/com/thridify/application/service/job/history/GetJobHistoryQuery.kt | class `GetJobHistoryQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/history/JobHistoryResult.kt | class `JobHistoryResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/listapikeyjobs/ListApiKeyJobsApplicationService.kt | class `ListApiKeyJobsApplicationService` | application | application | use-case-orchestration |  | JobRepository |  |
| src/main/kotlin/com/thridify/application/service/job/listapikeyjobs/ListApiKeyJobsQuery.kt | class `ListApiKeyJobsQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/job/listuserjobs/ListUserJobsApplicationService.kt | class `ListUserJobsApplicationService` | application | application | use-case-orchestration |  | JobRepository |  |
| src/main/kotlin/com/thridify/application/service/job/listuserjobs/ListUserJobsQuery.kt | class `ListUserJobsQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/plan/PlanResult.kt | class `PlanResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/plan/PlanResultMapper.kt | file `PlanResultMapper` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/plan/createplan/CreatePlanApplicationService.kt | class `CreatePlanApplicationService` | application | application | use-case-orchestration |  | BillingClient, PlanRepository |  |
| src/main/kotlin/com/thridify/application/service/plan/createplan/CreatePlanCommand.kt | class `CreatePlanCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/plan/deactivateplan/DeactivatePlanApplicationService.kt | class `DeactivatePlanApplicationService` | application | application | use-case-orchestration |  | PlanRepository |  |
| src/main/kotlin/com/thridify/application/service/plan/deactivateplan/DeactivatePlanCommand.kt | class `DeactivatePlanCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/plan/listactive/ListActivePlansApplicationService.kt | class `ListActivePlansApplicationService` | application | application | use-case-orchestration |  | PlanRepository |  |
| src/main/kotlin/com/thridify/application/service/plan/listplans/ListPlansApplicationService.kt | class `ListPlansApplicationService` | application | application | use-case-orchestration |  | PlanRepository |  |
| src/main/kotlin/com/thridify/application/service/plan/updateplan/UpdatePlanApplicationService.kt | class `UpdatePlanApplicationService` | application | application | use-case-orchestration |  | PlanRepository |  |
| src/main/kotlin/com/thridify/application/service/plan/updateplan/UpdatePlanCommand.kt | class `UpdatePlanCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/checkout/CheckoutResult.kt | class `CheckoutResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/checkout/CreateCheckoutSessionApplicationService.kt | class `CreateCheckoutSessionApplicationService` | application | application | use-case-orchestration |  | ApiKeyRepository, BillingClient, PlanRepository, SubscriptionRepository, TransactionProvider |  |
| src/main/kotlin/com/thridify/application/service/subscription/checkout/CreateCheckoutSessionCommand.kt | class `CreateCheckoutSessionCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/portal/CreateBillingPortalApplicationService.kt | class `CreateBillingPortalApplicationService` | application | application | use-case-orchestration |  | BillingClient, SubscriptionRepository |  |
| src/main/kotlin/com/thridify/application/service/subscription/portal/CreateBillingPortalCommand.kt | class `CreateBillingPortalCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/portal/PortalResult.kt | class `PortalResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/status/GetSubscriptionStatusApplicationService.kt | class `GetSubscriptionStatusApplicationService` | application | application | use-case-orchestration |  | SubscriptionRepository |  |
| src/main/kotlin/com/thridify/application/service/subscription/status/GetSubscriptionStatusQuery.kt | class `GetSubscriptionStatusQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/status/SubscriptionStatusResult.kt | class `SubscriptionStatusResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/subscription/webhook/HandleStripeWebhookApplicationService.kt | class `HandleStripeWebhookApplicationService` | application | application | use-case-orchestration |  | ApiKeyRepository, BillingClient, SubscriptionRepository, TransactionProvider |  |
| src/main/kotlin/com/thridify/application/service/subscription/webhook/HandleStripeWebhookCommand.kt | class `HandleStripeWebhookCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/webhook/WebhookResult.kt | class `WebhookResult` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/webhook/WebhookResultMapper.kt | file `WebhookResultMapper` | application | application (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/application/service/webhook/delete/DeleteWebhookApplicationService.kt | class `DeleteWebhookApplicationService` | application | application | use-case-orchestration |  | WebhookRepository |  |
| src/main/kotlin/com/thridify/application/service/webhook/delete/DeleteWebhookCommand.kt | class `DeleteWebhookCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/webhook/get/GetWebhookApplicationService.kt | class `GetWebhookApplicationService` | application | application | use-case-orchestration |  | WebhookRepository |  |
| src/main/kotlin/com/thridify/application/service/webhook/get/GetWebhookQuery.kt | class `GetWebhookQuery` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/application/service/webhook/set/SetWebhookApplicationService.kt | class `SetWebhookApplicationService` | application | application | use-case-orchestration |  | WebhookRepository |  |
| src/main/kotlin/com/thridify/application/service/webhook/set/SetWebhookCommand.kt | class `SetWebhookCommand` | application | application | command/query/result |  |  |  |
| src/main/kotlin/com/thridify/domain/access/ApiKeyPolicy.kt | class `ApiKeyPolicy` | domain | domain | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/domain/access/RequestRateLimiter.kt | interface `RequestRateLimiter` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/apikey/ApiKeyAuthEntity.kt | class `ApiKeyAuthEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/apikey/ApiKeyEntity.kt | class `ApiKeyEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/apikey/ApiKeyRepository.kt | interface `ApiKeyRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/apikey/ApiKeyWithPlanEntity.kt | class `ApiKeyWithPlanEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/billing/BillingClient.kt | interface `BillingClient` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/billing/BillingEvent.kt | interface `BillingEvent` | domain | domain (or shared if purely technical) | model/value/util |  |  | many-types-in-file: 6 |
| src/main/kotlin/com/thridify/domain/billing/BillingSubscription.kt | class `BillingSubscription` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/generation/CustomerWebhookClient.kt | interface `CustomerWebhookClient` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/generation/GenerationPolicy.kt | class `GenerationPolicy` | domain | domain | domain-service? |  |  | many-types-in-file: 5 |
| src/main/kotlin/com/thridify/domain/generation/GenerationProviderClient.kt | interface `GenerationProviderClient` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/generation/GenerationTaskDelivery.kt | interface `GenerationTaskDelivery` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/generation/GenerationTaskPublisher.kt | interface `GenerationTaskPublisher` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/generation/ImageStorage.kt | interface `ImageStorage` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/generation/JobNotification.kt | class `JobNotification` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/identity/IdentityPolicy.kt | class `IdentityPolicy` | domain | domain | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/domain/identity/PasswordHasher.kt | interface `PasswordHasher` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/identity/TokenClient.kt | interface `TokenClient` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/identity/UserEntity.kt | class `UserEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/identity/UserRepository.kt | interface `UserRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/job/JobEntity.kt | class `JobEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/job/JobHistoryEntity.kt | class `JobHistoryEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/job/JobHistoryRepository.kt | interface `JobHistoryRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/job/JobRepository.kt | interface `JobRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/outbox/OutboxMessageEntity.kt | class `OutboxMessageEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/outbox/OutboxRepository.kt | interface `OutboxRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/plan/PlanEntity.kt | class `PlanEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/plan/PlanPolicy.kt | class `PlanPolicy` | domain | domain | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/domain/plan/PlanRepository.kt | interface `PlanRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/subscription/SubscriptionEntity.kt | class `SubscriptionEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/subscription/SubscriptionPolicy.kt | class `SubscriptionPolicy` | domain | domain | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/domain/subscription/SubscriptionRepository.kt | interface `SubscriptionRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/subscription/SubscriptionWithPlanEntity.kt | class `SubscriptionWithPlanEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/transaction/TransactionProvider.kt | interface `TransactionProvider` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/domain/webhook/WebhookEntity.kt | class `WebhookEntity` | domain | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/domain/webhook/WebhookPolicy.kt | class `WebhookPolicy` | domain | domain | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/domain/webhook/WebhookRepository.kt | interface `WebhookRepository` | domain | domain | port |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/billing/StripeBillingClient.kt | class `StripeBillingClient` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/billing/StripeConfig.kt | class `StripeConfig` | infrastructure | infrastructure (tech config) or owning layer | configuration |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/config/FlywayConfig.kt | class `FlywayConfig` | infrastructure | infrastructure (tech config) or owning layer | configuration |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/config/JacksonConfig.kt | class `JacksonConfig` | infrastructure | infrastructure (tech config) or owning layer | configuration | jackson |  |  |
| src/main/kotlin/com/thridify/infrastructure/config/RabbitConfig.kt | class `RabbitConfig` | infrastructure | infrastructure | integration-adapter | rabbit |  |  |
| src/main/kotlin/com/thridify/infrastructure/config/S3Config.kt | class `S3Config` | infrastructure | infrastructure | integration-adapter | aws |  |  |
| src/main/kotlin/com/thridify/infrastructure/messaging/OutboxGenerationTaskPublisher.kt | class `OutboxGenerationTaskPublisher` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? | jackson | ObjectMapper, OutboxRepository |  |
| src/main/kotlin/com/thridify/infrastructure/messaging/RabbitGenerationTaskDelivery.kt | class `RabbitGenerationTaskDelivery` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? | jackson | ObjectMapper |  |
| src/main/kotlin/com/thridify/infrastructure/messaging/TaskProducer.kt | class `TaskProducer` | infrastructure | infrastructure | integration-adapter | jackson,rabbit | ObjectMapper, RabbitTemplate |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresApiKeyRepository.kt | class `PostgresApiKeyRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresJobHistoryRepository.kt | class `PostgresJobHistoryRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresJobRepository.kt | class `PostgresJobRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresOutboxRepository.kt | class `PostgresOutboxRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresPlanRepository.kt | class `PostgresPlanRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresSubscriptionRepository.kt | class `PostgresSubscriptionRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresUserRepository.kt | class `PostgresUserRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/PostgresWebhookRepository.kt | class `PostgresWebhookRepository` | infrastructure | infrastructure | persistence-adapter | jooq | DSLContext |  |
| src/main/kotlin/com/thridify/infrastructure/persistence/SpringTransactionProvider.kt | class `SpringTransactionProvider` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? | spring-tx |  |  |
| src/main/kotlin/com/thridify/infrastructure/provider/runpod/RunPodClient.kt | class `RunPodClient` | infrastructure | infrastructure | integration-adapter | http-client,spring-web |  |  |
| src/main/kotlin/com/thridify/infrastructure/security/JwtService.kt | class `JwtService` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/security/PasswordConfiguration.kt | class `PasswordConfiguration` | infrastructure | infrastructure (tech config) or owning layer | configuration |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/security/RateLimiterService.kt | class `RateLimiterService` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/security/SpringPasswordHasher.kt | class `SpringPasswordHasher` | infrastructure | infrastructure (keep; weak signal: domain) | domain-service? |  |  |  |
| src/main/kotlin/com/thridify/infrastructure/storage/StorageService.kt | class `StorageService` | infrastructure | infrastructure | integration-adapter | aws | S3Client |  |
| src/main/kotlin/com/thridify/infrastructure/webhook/HttpCustomerWebhookClient.kt | class `HttpCustomerWebhookClient` | infrastructure | infrastructure | integration-adapter | http-client,spring-web |  |  |
| src/main/kotlin/com/thridify/interfaces/messaging/TaskWorker.kt | class `TaskWorker` | interfaces | interfaces | entry-point | jackson,rabbit | DispatchGenerationTaskApplicationService, ObjectMapper |  |
| src/main/kotlin/com/thridify/interfaces/rest/AdminController.kt | class `AdminController` | interfaces | interfaces | entry-point | spring-web | CreatePlanApplicationService, DeactivatePlanApplicationService, ListPlansApplicationService, UpdatePlanApplicationService | several-app-services (check no single handler composes them): CreatePlanApplicationService,DeactivatePlanApplicationService,ListPlansApplicationService,UpdatePlanApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/ApiKeyController.kt | class `ApiKeyController` | interfaces | interfaces | entry-point | spring-web | CreateApiKeyApplicationService, ListApiKeysApplicationService, RevokeApiKeyApplicationService | several-app-services (check no single handler composes them): CreateApiKeyApplicationService,ListApiKeysApplicationService,RevokeApiKeyApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/AuthController.kt | class `AuthController` | interfaces | interfaces | entry-point | spring-web | LoginUserApplicationService, RegisterUserApplicationService | several-app-services (check no single handler composes them): LoginUserApplicationService,RegisterUserApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/GenerateController.kt | class `GenerateController` | interfaces | interfaces | entry-point | spring-web | GenerateModelApplicationService, GetJobApplicationService, GetJobHistoryApplicationService, ListApiKeyJobsApplicationService | several-app-services (check no single handler composes them): GenerateModelApplicationService,GetJobApplicationService,GetJobHistoryApplicationService,ListApiKeyJobsApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/JobController.kt | class `JobController` | interfaces | interfaces | entry-point | spring-web | GetJobHistoryApplicationService, ListUserJobsApplicationService | several-app-services (check no single handler composes them): GetJobHistoryApplicationService,ListUserJobsApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/PublicPlanController.kt | class `PublicPlanController` | interfaces | interfaces | entry-point | spring-web | ListActivePlansApplicationService |  |
| src/main/kotlin/com/thridify/interfaces/rest/SubscriptionController.kt | class `SubscriptionController` | interfaces | interfaces | entry-point | spring-web | CreateBillingPortalApplicationService, CreateCheckoutSessionApplicationService, GetSubscriptionStatusApplicationService | several-app-services (check no single handler composes them): CreateBillingPortalApplicationService,CreateCheckoutSessionApplicationService,GetSubscriptionStatusApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/WebhookController.kt | class `WebhookController` | interfaces | interfaces | entry-point | spring-web | DeleteWebhookApplicationService, GetWebhookApplicationService, SetWebhookApplicationService | several-app-services (check no single handler composes them): DeleteWebhookApplicationService,GetWebhookApplicationService,SetWebhookApplicationService |
| src/main/kotlin/com/thridify/interfaces/rest/config/SecurityConfig.kt | class `SecurityConfig` | interfaces | interfaces | configuration | spring-web |  |  |
| src/main/kotlin/com/thridify/interfaces/rest/dto/Dtos.kt | class `RegisterRequest` | interfaces | interfaces (or infrastructure if outbound payload) | api-or-message-dto | jackson |  | many-types-in-file: 19 |
| src/main/kotlin/com/thridify/interfaces/rest/dto/ResultMappers.kt | file `ResultMappers` | interfaces | interfaces (keep; weak signal: domain) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/interfaces/rest/exceptions/GlobalExceptionHandler.kt | class `GlobalExceptionHandler` | interfaces | interfaces | http-error-mapping | jackson,spring-web | ObjectMapper |  |
| src/main/kotlin/com/thridify/interfaces/rest/filter/ApiKeyAuthFilter.kt | class `ApiKeyAuthFilter` | interfaces | interfaces (keep; weak signal: domain) | domain-service? | spring-web | AuthorizeApiRequestApplicationService |  |
| src/main/kotlin/com/thridify/interfaces/rest/filter/JwtAuthFilter.kt | class `JwtAuthFilter` | interfaces | interfaces (keep; weak signal: domain) | domain-service? | spring-web | AuthenticateJwtApplicationService |  |
| src/main/kotlin/com/thridify/interfaces/scheduled/OutboxPublisher.kt | class `OutboxPublisher` | interfaces | interfaces | entry-point |  | PublishPendingGenerationTasksApplicationService |  |
| src/main/kotlin/com/thridify/interfaces/webhook/ProviderWebhookController.kt | class `ProviderWebhookController` | interfaces | interfaces | entry-point | spring-web | HandleGenerationCallbackApplicationService |  |
| src/main/kotlin/com/thridify/interfaces/webhook/StripeWebhookController.kt | class `StripeWebhookController` | interfaces | interfaces | entry-point | spring-web | HandleStripeWebhookApplicationService |  |
| src/main/kotlin/com/thridify/shared/exception/ApiException.kt | class `ApiException` | shared | domain (or shared if purely technical) | model/value/util |  |  | many-types-in-file: 5 |
| src/main/kotlin/com/thridify/shared/message/GenerationTaskMessage.kt | class `GenerationTaskMessage` | shared | domain (or shared if purely technical) | model/value/util |  |  |  |
| src/main/kotlin/com/thridify/shared/metrics/AppMetrics.kt | class `AppMetrics` | shared | domain | domain-service? | micrometer |  |  |
