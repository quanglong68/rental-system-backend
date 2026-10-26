# Properties Service — Architecture Guidelines

## 1. Purpose

This document defines the source code architecture and coding standards for the `properties-service` project.

All AI coding agents, including Codex, must read and follow this document before creating, modifying, refactoring, or reviewing source code.

### Core objectives

- Apply Feature-based Architecture.
- Separate API, Application, Domain, and Infrastructure responsibilities.
- Keep business logic independent of HTTP, database, and external infrastructure details.
- Use clear DTO, Mapper, Repository, Service, and configuration boundaries.
- Maintain a consistent package structure across business features.
- Preserve existing API contracts, database schemas, migrations, and business behavior.
- Avoid unnecessary abstractions, duplicated code, and oversized classes.
- Make the codebase easy to maintain, test, and extend.

**Important:** Transfer architectural principles from Signify BE, not Signify-specific business features.

---

## 2. Project Context

### Project information

- Project name: `properties-service`
- Business domain: Properties and rental property management
- Java version: Java 21
- Framework: Spring Boot
- Build system: Gradle with Kotlin DSL
- Database: PostgreSQL
- Database migration tool: Flyway
- Base package: `fptu.semester8.mss301.propertiesservice`

### Existing technical conventions

The project currently uses or has been configured to use:

- Spring Web
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Spring Boot Flyway integration
- Kafka
- Spring Boot Actuator

The existing application configuration includes database schema settings, Flyway migration configuration, Kafka configuration, and multipart upload limits.

Before changing dependencies or configuration, inspect the current `build.gradle.kts`, `application.yaml`, and existing source code.

Do not add, remove, or upgrade dependencies without a clear technical requirement.

### Existing database considerations

The project has existing Flyway migration files under:

`src/main/resources/db/migration/`

The existing database includes Vietnam administrative location data, including province and ward tables.

Before modifying persistence code or database migrations, inspect all existing migration files and current entity mappings.

Do not assume that a table, column, relationship, or feature can be renamed or removed simply to match an example in this document.

---

## 3. Architectural Style

Use a combination of:

1. **Feature-based organization:** Group source code by business feature.
2. **Layered architecture:** Separate API, application, domain, and infrastructure responsibilities.
3. **Clean Architecture principles:** Keep core business rules independent of delivery mechanisms and infrastructure where practical.
4. **Dependency inversion:** Business logic should depend on abstractions when doing so provides a meaningful architectural benefit.

The primary organizational rule is:

> Organize code by business feature first, and by technical responsibility inside each feature.

Do not organize the entire project into large global packages such as `controllers`, `services`, `repositories`, and `entities` if this causes unrelated business features to become tightly coupled.

Prefer feature-specific packages that keep related classes together.

### Architectural example

A province-related feature should keep its API, application logic, domain model, persistence implementation, and mapping code within the province feature.

A ward-related feature should follow the same conventions when it has sufficient responsibilities to justify a separate feature.

Features may communicate through application services or clearly defined interfaces rather than accessing each other's internal implementation details directly.

---

## 4. Target Package Structure

The following is a target architecture, not an instruction to create every directory immediately.

Adapt the structure to the existing codebase and actual business requirements.

```text
src/
├── main/
│   ├── java/
│   │   └── fptu/
│   │       └── semester8/
│   │           └── mss301/
│   │               └── propertiesservice/
│   │                   ├── PropertiesServiceApplication.java
│   │                   │
│   │                   ├── common/
│   │                   │   ├── exception/
│   │                   │   ├── response/
│   │                   │   ├── validation/
│   │                   │   └── util/
│   │                   │
│   │                   ├── config/
│   │                   │   ├── JacksonConfig.java
│   │                   │   ├── JpaConfig.java
│   │                   │   └── KafkaConfig.java
│   │                   │
│   │                   └── features/
│   │                       ├── province/
│   │                       │   ├── api/
│   │                       │   │   ├── ProvinceController.java
│   │                       │   │   └── dto/
│   │                       │   │
│   │                       │   ├── application/
│   │                       │   │   ├── ProvinceService.java
│   │                       │   │   └── port/
│   │                       │   │       └── out/
│   │                       │   │
│   │                       │   ├── domain/
│   │                       │   │   ├── model/
│   │                       │   │   └── repository/
│   │                       │   │
│   │                       │   ├── infrastructure/
│   │                       │   │   └── persistence/
│   │                       │   │       ├── entity/
│   │                       │   │       └── repository/
│   │                       │   │
│   │                       │   └── mapper/
│   │                       │
│   │                       ├── ward/
│   │                       │   ├── api/
│   │                       │   │   ├── WardController.java
│   │                       │   │   └── dto/
│   │                       │   ├── application/
│   │                       │   ├── domain/
│   │                       │   ├── infrastructure/
│   │                       │   │   └── persistence/
│   │                       │   └── mapper/
│   │                       │
│   │                       ├── property/
│   │                       ├── building/
│   │                       ├── room/
│   │                       └── integration/
│   │
│   └── resources/
│       ├── application.yaml
│       └── db/
│           └── migration/
│
└── test/
    └── java/
        └── fptu/
            └── semester8/
                └── mss301/
                    └── propertiesservice/
                        └── features/
```

### Structure rules

- Keep `PropertiesServiceApplication.java` in the root package.
- Use the base package `fptu.semester8.mss301.propertiesservice`.
- Put business features inside `features/`.
- Keep shared, genuinely cross-feature components inside `common/`.
- Keep Spring configuration classes inside `config/` or a more specific configuration package when appropriate.
- Keep database migrations under the existing Flyway migration location.
- Create only features supported by the existing domain and requirements.
- Do not create empty packages merely to make the directory tree look complete.
- Do not rename existing features until their responsibilities and dependencies have been inspected.

The feature list above is illustrative. If a feature does not exist in the current project, do not create its implementation just because it appears in this example.

---

## 5. Responsibilities of Each Layer

### 5.1 API Layer

Package:

`features/<feature>/api/`

Responsibilities:

- Expose REST endpoints.
- Receive HTTP requests.
- Validate incoming request DTOs.
- Call application services.
- Convert application results into response DTOs.
- Return appropriate HTTP status codes.

Controllers must not contain complex business rules or directly manipulate JPA entities.

Example:

```java
@RestController
@RequestMapping("/api/v1/provinces")
@RequiredArgsConstructor
public class ProvinceController {

    private final ProvinceService provinceService;

    @GetMapping
    public ResponseEntity<List<ProvinceResponse>> getProvinces() {
        return ResponseEntity.ok(provinceService.getProvinces());
    }
}
```

This is a structural example. Adapt it to the existing API contract, naming conventions, response format, and actual service implementation.

### 5.2 API DTOs

Package:

`features/<feature>/api/dto/`

Use DTOs to define the HTTP request and response contracts.

Recommended naming conventions:

- `CreateProvinceRequest`
- `UpdateProvinceRequest`
- `ProvinceResponse`
- `ProvinceSummaryResponse`

Rules:

- Do not expose persistence entities directly through REST endpoints.
- Use Bean Validation annotations where applicable.
- Use `@Valid` to trigger request validation.
- Keep request DTOs and response DTOs separate when their responsibilities differ.
- Do not include database-only fields in API responses without a clear requirement.
- Preserve existing JSON field names and response compatibility unless an API change is explicitly requested.

### 5.3 Application Layer

Package:

`features/<feature>/application/`

Responsibilities:

- Coordinate use cases.
- Apply business workflows.
- Coordinate domain objects and repositories.
- Define transaction boundaries.
- Invoke external services through suitable abstractions.
- Return application results to the API layer.

Use meaningful service names, such as:

- `ProvinceService`
- `WardService`
- `PropertyService`
- `BuildingService`
- `RoomService`

These names are examples. Follow existing naming conventions when the equivalent classes already exist.

Rules:

- Avoid putting all business logic into controllers.
- Do not make application services depend on HTTP-specific objects such as `HttpServletRequest` unless the use case genuinely requires them.
- Keep each service focused on a clear use case or cohesive business responsibility.
- Avoid oversized services that handle unrelated features.
- Do not create multiple layers of service classes that merely forward calls without adding meaningful behavior.

### 5.4 Domain Layer

Package:

`features/<feature>/domain/`

Responsibilities:

- Represent business concepts and rules.
- Define domain models when useful.
- Define repository abstractions when they help isolate business logic from persistence.
- Contain domain-specific validation and behavior that should not depend on Spring MVC or database implementation details.

Possible components:

- Domain models
- Domain-specific value objects
- Business exceptions
- Repository interfaces

Rules:

- Do not place HTTP request or response DTOs in the domain layer.
- Avoid coupling core business rules to controllers or web response types.
- Do not introduce a separate domain model for every JPA entity without a meaningful reason.
- Existing JPA entities may serve as domain models in simple CRUD features if that is appropriate for the project.
- Use a separate domain model when business complexity or infrastructure isolation justifies the additional mapping.

The goal is clear responsibility separation, not unnecessary abstraction.

### 5.5 Infrastructure Layer

Package:

`features/<feature>/infrastructure/`

Responsibilities:

- Implement technical details required by application and domain logic.
- Integrate with PostgreSQL, JPA, Kafka, file storage, or external APIs.
- Implement repository interfaces when those abstractions are used.
- Handle persistence-specific queries and mappings.

For persistence-related code, prefer:

```text
infrastructure/
└── persistence/
    ├── entity/
    ├── repository/
    └── adapter/
```

Use `adapter/` only when there is an actual interface implementation or adapter responsibility.

Rules:

- Keep database-specific implementation details out of controllers.
- Avoid placing external integration logic in unrelated business features.
- Use Spring Data JPA repositories where appropriate.
- Do not introduce an elaborate adapter hierarchy for simple persistence operations.
- Preserve the current database schema and migration behavior.

### 5.6 Mapper Layer

Package:

`features/<feature>/mapper/`

Responsibilities:

- Convert request DTOs into application inputs or domain objects where appropriate.
- Convert domain models or persistence results into response DTOs.
- Keep mapping logic out of controllers and large application services.

Example responsibilities:

- Province to ProvinceResponse
- Ward to WardResponse
- Property to PropertyResponse

Rules:

- Use MapStruct only if it is already available or its introduction is justified.
- Manual mapping is acceptable for small mappings.
- Do not duplicate the same mapping logic across multiple classes.
- Do not put business decisions inside mappers.
- Do not blindly map every entity field into an API response.

---

## 6. Dependency Rules

Dependencies should follow this general direction:

```text
API
 |
 v
Application
 |
 v
Domain
 ^
 |
Infrastructure
```

More precisely:

- API depends on the application layer.
- Application coordinates domain behavior and required abstractions.
- Domain should not depend on API controllers or infrastructure implementations.
- Infrastructure implements technical details and repository ports where applicable.
- Spring configuration may wire the components together.

When repository abstractions are useful, application or domain code should depend on the abstraction rather than a concrete persistence implementation.

Avoid circular dependencies between features.

For example, a ward use case may need to verify that a province exists. It should do this through an appropriate application-level contract or domain abstraction rather than directly accessing the province feature's internal JPA repository.

Do not force every feature to use ports and adapters if doing so adds complexity without providing useful separation.

---

## 7. Feature Boundaries

Each feature owns its related business behavior and internal implementation.

Possible business areas include:

- `province`: province-related operations.
- `ward`: ward-related operations.
- `property`: property-related operations, if this is an actual business concept in the existing system.
- `building`: building management, if implemented in this service.
- `room`: room management, if implemented in this service.
- `integration`: cross-system integration when it does not belong naturally to one feature.

The actual feature list must be determined by inspecting the current source code, database migrations, API endpoints, and project requirements.

### Feature interaction rules

- Avoid importing another feature's persistence entities into API responses.
- Avoid accessing another feature's internal repositories directly.
- Prefer explicit application interfaces for cross-feature operations when needed.
- Avoid duplicating business rules across features.
- Avoid creating a separate feature for every entity if several entities form one cohesive business capability.
- Keep common utilities small and genuinely reusable.

Do not introduce Signify features such as `chat`, `call`, `ai`, or `notification` unless they are independently required by the Properties Service domain.

---

## 8. Shared Components

Package:

`common/`

Use this package only for components shared across multiple features.

Possible responsibilities:

```text
common/
├── exception/
├── response/
├── validation/
└── util/
```

Examples:

- Shared exception handling
- A consistent API response format, if the project uses one
- Reusable validation utilities
- Small, stateless utility functions

Rules:

- Do not move feature-specific business logic into `common/`.
- Do not create a generic `BaseService`, `BaseController`, or `BaseRepository` without a demonstrated need.
- Do not use `utils/` as a dumping ground for unrelated logic.
- Avoid global mutable state.
- Keep exception messages meaningful and safe to expose.

If a shared component becomes large or belongs to a specific business capability, reconsider its package placement.

---

## 9. Exception Handling and API Responses

Use a consistent error-handling strategy across the service.

Where appropriate:

- Validate HTTP requests at the API boundary.
- Use domain or application exceptions for business failures.
- Translate exceptions into HTTP responses at the API boundary.
- Use `@RestControllerAdvice` for centralized REST exception handling.
- Return clear, stable error codes or messages if the current API contract supports them.
- Do not expose stack traces, SQL statements, database credentials, or internal implementation details to clients.

Do not replace an existing response envelope or error format without checking its consumers first.

Avoid catching exceptions only to log and suppress them.

---

## 10. Database and Flyway Rules

The project uses PostgreSQL and Flyway.

Migration directory:

`src/main/resources/db/migration/`

### Required rules

- Use Flyway migrations to manage schema changes.
- Inspect all existing migrations before modifying database-related code.
- Never edit an already-applied migration merely to change the historical schema definition.
- Create a new versioned migration for a required schema change.
- Preserve existing table names, column names, constraints, indexes, and relationships unless a change is explicitly required.
- Keep JPA mappings consistent with the actual database schema.
- Do not use Hibernate schema generation as a replacement for Flyway migrations.
- Preserve the current database schema configuration.
- Do not drop or recreate existing tables as a shortcut for resolving migration errors.
- Do not insert production-specific or environment-specific secrets into migration files.

The current application configuration uses `ddl-auto: none`. Preserve this behavior unless there is a clear, reviewed requirement to change it.

### Migration naming

Follow the existing Flyway naming convention, typically:

```text
V1__create_location_tables.sql
V2__create_building_table.sql
V3__create_room_tables.sql
```

These filenames are examples of the existing project convention, not permission to replace or rename existing migrations.

Before adding a migration, inspect the current migration version and Flyway history.

Do not invent database columns or relationships without checking the actual business requirements.

### Entity rules

- Map entities to the real database tables and columns.
- Use explicit mappings when database naming differs from Java naming.
- Avoid exposing entities directly from controllers.
- Avoid eager loading large object graphs by default.
- Be careful with bidirectional relationships and JSON serialization.
- Use transactions for operations that must be atomic.
- Avoid adding indexes or constraints without considering query patterns and existing schema requirements.

---

## 11. Kafka and External Integrations

The project includes Kafka dependencies or configuration.

Do not introduce Kafka producers, consumers, event classes, or topic definitions merely because Kafka exists in the build configuration.

Before implementing an integration:

1. Inspect the existing Kafka configuration.
2. Identify the business event or integration requirement.
3. Define the event contract and its ownership.
4. Keep event publishing and consumption separate from controller logic.
5. Consider retry behavior, duplicate delivery, idempotency, and failure handling.
6. Preserve existing topic names and event payload compatibility.
7. Add tests for relevant success and failure cases.

If an integration does not exist yet, implement it only when requested by the project requirements.

---

## 12. Configuration and Security

Keep environment-specific configuration outside business logic.

Rules:

- Use `application.yaml` for appropriate application defaults.
- Use environment variables or the existing secret-management mechanism for sensitive values.
- Never hardcode database passwords, API keys, tokens, or credentials.
- Do not commit real secrets.
- Avoid scattering configuration values throughout services.
- Use typed configuration properties when configuration is complex or shared.
- Keep configuration classes focused on infrastructure setup.

Do not introduce authentication or authorization mechanisms from Signify simply because they exist in that project.

First inspect the MSS301 system architecture and the Properties Service's existing security requirements.

If authentication is handled by an API Gateway or another service, follow the actual system contract instead of independently inventing a new authentication mechanism.

---

## 13. Logging and Observability

Use structured, useful logging.

Rules:

- Log meaningful application events and unexpected failures.
- Avoid logging passwords, tokens, secrets, or sensitive personal information.
- Avoid logging the same exception repeatedly at every layer.
- Do not use console printing as a replacement for application logging.
- Preserve existing Actuator and health-check configuration.
- Avoid introducing expensive logging or monitoring dependencies without justification.

Use appropriate log levels:

- `DEBUG`: diagnostic details.
- `INFO`: meaningful lifecycle or business events.
- `WARN`: recoverable abnormal situations.
- `ERROR`: unexpected failures that require investigation.

---

## 14. Testing Strategy

Tests should reflect the architecture and the actual behavior of each feature.

Recommended test categories:

### Unit tests

Test business rules and application behavior without starting the entire application where practical.

### Web/API tests

Test request validation, response structures, status codes, and exception handling.

### Persistence tests

Test JPA mappings and repository queries against an appropriate test database or supported integration-test environment.

### Integration tests

Test important interactions among the application, database, Kafka, or other infrastructure when relevant.

### Test conventions

- Keep tests under `src/test/java` using the corresponding package structure.
- Use meaningful test names.
- Test successful and unsuccessful cases.
- Test validation failures and important business rules.
- Avoid tests that only verify trivial getters or setters.
- Do not delete or weaken existing tests just to make the build pass.
- Do not claim tests passed unless they were actually executed successfully.

Prefer testing observable behavior rather than private implementation details.

---

## 15. Naming Conventions

Use consistent Java naming conventions.

| Component | Naming example |
|---|---|
| Controller | `ProvinceController` |
| Application service | `ProvinceService` |
| Request DTO | `CreateProvinceRequest` |
| Response DTO | `ProvinceResponse` |
| JPA entity | `ProvinceEntity`, if this matches the existing convention |
| Spring Data repository | `ProvinceJpaRepository` |
| Domain repository interface | `ProvinceRepository` |
| Mapper | `ProvinceMapper` |
| Configuration | `KafkaConfig` |
| Business exception | `ProvinceNotFoundException` |

These are examples. Preserve the existing convention if changing names would create unnecessary churn.

Class names should describe their responsibility rather than their implementation history.

Avoid vague names such as `CommonService`, `DataManager`, `Helper`, and `BaseHandler` unless their purpose is genuinely clear.

---

## 16. Code Quality Rules

- Follow Java 21 language and project conventions.
- Use constructor injection; prefer `@RequiredArgsConstructor` when Lombok is already configured and consistent with the project.
- Avoid field injection.
- Keep methods focused on one clear responsibility.
- Prefer immutable data where practical.
- Avoid unnecessary inheritance and deep class hierarchies.
- Use interfaces when they define a meaningful boundary, not merely to duplicate every implementation class.
- Avoid excessive `Optional` usage in fields, DTOs, and method parameters.
- Avoid returning `null` when a clear alternative exists.
- Use meaningful names and avoid unexplained magic values.
- Keep transactions at appropriate application or use-case boundaries.
- Avoid unnecessary dependencies between features.
- Do not refactor unrelated code as part of a small feature request.

Do not introduce new libraries or architectural patterns unless their benefits justify their maintenance cost.

---

## 17. Directory and `.gitkeep` Rules

Use `.gitkeep` only when an intentionally empty directory must be preserved in Git.

Rules:

- Add `.gitkeep` only to directories that are intentionally empty.
- Do not add `.gitkeep` to directories that already contain tracked files.
- Do not create empty Java classes just to represent future functionality.
- Do not create a large number of speculative directories.
- Remove unnecessary `.gitkeep` files once a directory contains actual tracked files, if appropriate for the repository's conventions.
- Do not create placeholder packages for features that have not been confirmed.

The goal is a meaningful source structure, not a large directory tree.

---

## 18. Rules for AI Coding Agents

Before modifying the project, the AI agent must:

1. Read this `ARCHITECTURE.md`.
2. Read `AGENTS.md` if it exists.
3. Inspect the current source tree.
4. Inspect `build.gradle.kts`.
5. Inspect `application.yaml` and related configuration.
6. Inspect the relevant Flyway migration files.
7. Inspect existing entities, repositories, controllers, services, and tests.
8. Identify the requested feature and its current implementation.
9. Explain the intended changes before performing a large refactor.

### When implementing a new feature

The AI agent must:

1. Identify the actual business requirement.
2. Determine whether the feature already exists under another package or name.
3. Follow the established feature-based package organization.
4. Add only the layers necessary for the feature.
5. Reuse existing shared components where appropriate.
6. Preserve API and database compatibility.
7. Add or update tests.
8. Run the relevant Gradle build and tests.
9. Report the files changed, design decisions, and actual verification results.

### When refactoring existing code

The AI agent must:

1. Inspect existing dependencies and call sites.
2. Preserve existing behavior unless a behavior change is requested.
3. Avoid changing API paths, HTTP status codes, JSON fields, or database contracts unintentionally.
4. Avoid rewriting working code merely to make it look different.
5. Move classes incrementally and update package declarations and imports.
6. Check component scanning and Spring dependency injection after moving classes.
7. Check entity scanning and repository scanning if their configuration depends on package locations.
8. Check tests and application startup after the refactor.
9. Avoid mixing unrelated feature changes into the same refactor.

### Prohibited behavior

The AI agent must not:

- Copy Signify-specific business modules into Properties Service.
- Replace Flyway with Liquibase.
- Replace PostgreSQL or change the database schema without an explicit requirement.
- Delete migrations or reset migration history to avoid resolving an error.
- Generate speculative business logic from package names alone.
- Create unnecessary layers for trivial CRUD operations.
- Introduce a new dependency without justification.
- Claim a build or test succeeded when it was not executed.
- Change unrelated features during a targeted task.

---

## 19. Incremental Architecture Migration

If the current project does not yet follow this architecture, migrate incrementally.

Recommended order:

1. Inspect and document the existing architecture.
2. Identify the current business features and their dependencies.
3. Establish consistent feature boundaries.
4. Refactor one feature at a time.
5. Separate controller responsibilities from application logic.
6. Introduce DTOs and mappers where needed.
7. Isolate persistence details where doing so provides meaningful value.
8. Extract genuinely shared components into `common/`.
9. Update and execute tests after each significant change.
10. Verify the complete Gradle build before considering the migration complete.

Do not move the entire codebase in one large, unverified operation.

A successful migration must preserve existing behavior while making responsibilities and dependencies clearer.

---

## 20. Definition of Done

A task is complete when:

- The implementation follows the relevant architecture rules in this document.
- The code belongs to the correct business feature.
- Each class has a clear responsibility.
- Dependencies follow the intended architectural boundaries.
- API contracts remain compatible unless changes were requested.
- Database mappings and Flyway migrations remain consistent.
- Required tests have been added or updated.
- Relevant tests and build checks have been executed.
- No secrets or unnecessary dependencies have been introduced.
- No unrelated business logic has been changed.
- Any limitations or failed checks are explicitly reported.

## Final Principle

**Use Signify BE as the architectural reference, but use Properties Service as the source of truth for business logic, database design, API contracts, dependencies, and project requirements.**

The objective is to achieve the same level of architectural consistency and maintainability without copying unrelated functionality or introducing unnecessary complexity.