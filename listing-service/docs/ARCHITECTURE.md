# Listing Service — Architecture Guidelines

## 1. Purpose

This document defines the source code architecture and coding standards for the `Listing-Service` project.

All AI coding agents, including Codex and other coding assistants, must read and follow this document before creating, modifying, refactoring, or reviewing source code.

### Core objectives

- Apply Feature-based Architecture.
- Separate API, Application, Domain, and Infrastructure responsibilities.
- Keep business logic independent of HTTP, database, and external infrastructure details where practical.
- Use clear DTO, Mapper, Repository, Service, and configuration boundaries.
- Maintain a consistent package structure across business features.
- Preserve existing API contracts, database schemas, Flyway migrations, and business behavior.
- Avoid unnecessary abstractions, duplicated code, and oversized classes.
- Make the codebase easy to maintain, test, and extend.
- Integrate with other Rental System services through explicit and verified contracts.

**Important:** Transfer architectural principles from Signify BE, not Signify-specific business features.

Listing Service is part of the MSS301 Rental System. Its own business requirements, database migrations, API contracts, and existing implementation are the sources of truth.

---

## 2. Project Context

### Project information

- Project name: `Listing-Service`
- Business domain: Room listing, rental requests, appointments, and roommate-related functionality, subject to verification against actual requirements.
- Programming language: Java
- Java version: Follow the version declared in the current `pom.xml` and existing project configuration.
- Framework: Spring Boot
- Build system: Maven
- Database: PostgreSQL
- Database migration tool: Flyway
- Base package: Inspect the existing source code and `pom.xml` before assuming or changing the package name.

### Existing technical conventions

The project may use or be configured to use the following technologies:

- Spring Web MVC
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- Kafka
- Spring Cloud OpenFeign
- Spring Cloud Netflix Eureka
- Springdoc OpenAPI
- MapStruct
- Other dependencies already declared in the current `pom.xml`

This list is descriptive, not an instruction to introduce every technology into every feature.

Before changing dependencies or configuration, inspect:

- `pom.xml`
- `src/main/resources/application.yml` or `application.yaml`
- Existing Java source code
- Existing Flyway migrations
- Docker and Docker Compose configuration, where relevant
- Existing tests
- Related service contracts and architecture documentation, where available

Do not add, remove, or upgrade dependencies without a clear technical requirement.

Do not assume that a dependency is actively used merely because it appears in `pom.xml`.

### Existing database considerations

The project uses PostgreSQL and Flyway.

Existing migrations may define tables and relationships for:

- Room listings
- Rental requests
- Appointments
- Roommate posts
- Roommate applications
- Processed events
- Outbox events

The actual database design must be verified against the existing migration files.

Before modifying persistence code or migrations, inspect the relevant existing migration files and entity mappings.

Do not assume that every table listed above has a fully implemented application feature.

Do not rename or remove an existing table, column, constraint, index, or relationship simply to match an example in this document.

### Database and schema distinction

A PostgreSQL database and a PostgreSQL schema are different concepts.

For example:

- Database: `listing_db`

These are illustrative values, not mandatory configuration.

Always inspect the actual datasource URL, environment variables, PostgreSQL configuration, Flyway configuration, and current migration setup before deciding which database or schema the service uses.

Do not change the database name or schema merely to match an example.

---

## 3. Architectural Style

Use a combination of:

1. **Feature-based organization:** Group source code by business feature.
2. **Layered architecture:** Separate API, application, domain, and infrastructure responsibilities.
3. **Clean Architecture principles:** Keep core business rules independent of delivery mechanisms and infrastructure details where practical.
4. **Dependency inversion:** Depend on abstractions when doing so provides a meaningful architectural benefit.

The primary organizational rule is:

> Organize code by business feature first, and by technical responsibility inside each feature.

Avoid organizing the entire project into large global packages such as `controllers`, `services`, `repositories`, and `entities` when this causes unrelated business features to become tightly coupled.

Prefer feature-specific packages that keep related classes together.

Do not force every feature to implement every architectural layer. Simple CRUD functionality may use a smaller structure when additional layers would add complexity without meaningful benefits.

---

## 4. Target Package Structure

The following structure is a target architecture, not an instruction to create every package immediately.

Adapt it to the existing codebase, actual database migrations, API contracts, and business requirements.

```text
src/
├── main/
│   ├── java/
│   │   └── <existing-base-package>/
│   │       ├── ListingServiceApplication.java
│   │       │
│   │       ├── common/
│   │       │   ├── exception/
│   │       │   ├── response/
│   │       │   ├── validation/
│   │       │   └── util/
│   │       │
│   │       ├── config/
│   │       │   ├── JpaConfig.java
│   │       │   ├── KafkaConfig.java
│   │       │   └── other-required-configurations/
│   │       │
│   │       ├── listing/
│   │       │   ├── api/
│   │       │   │   ├── ListingController.java
│   │       │   │   └── dto/
│   │       │   │       ├── request/
│   │       │   │       └── response/
│   │       │   ├── application/
│   │       │   ├── domain/
│   │       │   │   ├── model/
│   │       │   │   └── repository/
│   │       │   ├── infrastructure/
│   │       │   │   └── persistence/
│   │       │   │       ├── entity/
│   │       │   │       ├── repository/
│   │       │   │       └── adapter/
│   │       │   └── mapper/
│   │       │
│   │       ├── rentalrequest/
│   │       ├── appointment/
│   │       ├── roommate/
│   │       │   ├── post/
│   │       │   └── application/
│   │       │
│   │       ├── integration/
│   │       │   ├── property/
│   │       │   └── other-confirmed-integrations/
│   │       │
│   │       └── event/
│   │           ├── producer/
│   │           ├── consumer/
│   │           └── dto/
│   │
│   └── resources/
│       ├── application.yml
│       └── db/
│           └── migration/
│               ├── V01__...
│               ├── V02__...
│               └── ...
│
└── test/
    └── java/
        └── <existing-base-package>/
            ├── listing/
            ├── rentalrequest/
            ├── appointment/
            ├── roommate/
            └── integration/
```

### Structure rules

- Keep `ListingServiceApplication.java` in the existing root package.
- Preserve the existing base package unless a package migration is explicitly required.
- Keep business features organized by feature.
- Keep shared, genuinely cross-feature components inside `common/`.
- Keep Spring configuration classes inside `config/` or a more specific configuration package when appropriate.
- Keep Flyway migrations under the existing migration directory.
- Create only features supported by the existing domain and requirements.
- Do not create empty packages merely to make the directory tree look complete.
- Do not rename existing features until their responsibilities and dependencies have been inspected.
- Do not move the entire codebase into a new package structure as part of an unrelated feature implementation.

The structure above is illustrative. Existing package names and classes take precedence until a deliberate, reviewed migration is approved.

---

## 5. Responsibilities of Each Layer

### 5.1 API Layer

Recommended package:

`<feature>/api/`

Responsibilities:

- Expose REST endpoints.
- Receive HTTP requests.
- Validate incoming request DTOs.
- Call application services.
- Convert application results into response DTOs.
- Return appropriate HTTP status codes.
- Preserve the existing API contract unless an API change is explicitly requested.

Controllers must not contain complex business rules or directly manipulate JPA entities.

Controllers should not access Spring Data repositories directly when the application service is responsible for the relevant use case.

Example:

```java
@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getListing(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(listingService.getListing(id));
    }
}
```

This example demonstrates structure only. Adapt the path, return type, response envelope, ID serialization, and naming conventions to the actual project contract.

Do not introduce this endpoint if an equivalent endpoint already exists under a different path or implementation.

### 5.2 API DTOs

Recommended package:

`<feature>/api/dto/`

Use DTOs to define HTTP request and response contracts.

Possible naming conventions:

- `CreateListingRequest`
- `UpdateListingRequest`
- `ListingResponse`
- `ListingSummaryResponse`
- `CreateRentalRequestRequest`
- `RentalRequestResponse`

These names are examples, not confirmation of the required API.

Rules:

- Do not expose persistence entities directly through REST endpoints.
- Use Bean Validation annotations where applicable.
- Use `@Valid` to trigger request validation.
- Keep request DTOs and response DTOs separate when their responsibilities differ.
- Do not include database-only fields in API responses without a clear requirement.
- Preserve existing JSON field names and response compatibility.
- Do not allow clients to change server-managed fields through general-purpose update DTOs.
- Do not trust client-supplied property or room snapshots without validating their source and authorization.

For IDs, inspect the existing system's serialization convention.

If Snowflake IDs are represented as Java `Long` and PostgreSQL `BIGINT`, consider JavaScript numeric precision when returning those IDs to a frontend. Do not change the API representation without checking compatibility with existing consumers.

### 5.3 Application Layer

Recommended package:

`<feature>/application/`

Responsibilities:

- Coordinate use cases.
- Apply business workflows.
- Coordinate domain objects and repository abstractions.
- Define transaction boundaries.
- Invoke external services through suitable abstractions.
- Return application results to the API layer.

Possible service names:

- `ListingService`
- `RentalRequestService`
- `AppointmentService`
- `RoommatePostService`
- `RoommateApplicationService`

These are examples. Implement a service only when supported by actual business requirements.

Rules:

- Avoid putting business logic into controllers.
- Do not make application services depend on HTTP-specific objects such as `HttpServletRequest` unless the use case genuinely requires them.
- Keep each service focused on a cohesive business responsibility.
- Avoid oversized services that handle unrelated features.
- Do not create multiple service layers that merely forward calls without adding meaningful behavior.
- Put transactions at appropriate application or use-case boundaries.
- Do not assume that an operation across multiple services can participate in a single local database transaction.

### 5.4 Domain Layer

Recommended package:

`<feature>/domain/`

Responsibilities:

- Represent business concepts and rules.
- Define domain models when useful.
- Define domain-specific validation and behavior.
- Define repository interfaces when they help isolate business logic from persistence.

Possible components:

- Domain models
- Value objects
- Business exceptions
- Repository interfaces
- Domain rules and policies

Rules:

- Do not place HTTP request or response DTOs in the domain layer.
- Avoid coupling core business rules to controllers or web response types.
- Do not introduce a separate domain model for every JPA entity without a meaningful reason.
- Existing JPA entities may serve as domain models in simple CRUD features if appropriate.
- Use a separate domain model when business complexity or infrastructure isolation justifies the additional mapping.

The goal is clear responsibility separation, not unnecessary abstraction.

### 5.5 Infrastructure Layer

Recommended package:

`<feature>/infrastructure/`

Responsibilities:

- Implement technical details required by application and domain logic.
- Integrate with PostgreSQL, JPA, Kafka, file storage, or external APIs where required.
- Implement repository interfaces when those abstractions are used.
- Handle persistence-specific queries and mappings.
- Implement technical adapters for external service integrations.

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
- Use Spring Data JPA where appropriate.
- Do not introduce an elaborate adapter hierarchy for simple persistence operations.
- Preserve the current PostgreSQL schema and Flyway migration behavior.
- Do not introduce cross-service database joins or direct access to another service's private database.
- Do not use JPA relationships to model entities owned by a different microservice.

### 5.6 Mapper Layer

Recommended package:

`<feature>/mapper/`

Responsibilities:

- Convert request DTOs into application inputs or domain objects where appropriate.
- Convert domain models or persistence results into response DTOs.
- Keep mapping logic out of controllers and large application services.

Rules:

- Use MapStruct only if it is already available or its introduction is justified.
- Manual mapping is acceptable for small mappings.
- Do not duplicate the same mapping logic across multiple classes.
- Do not put business decisions inside mappers.
- Do not blindly map every entity field into an API response.
- Inspect the existing mapping conventions before adding a new mapper.

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
- Spring configuration wires components together.

When repository abstractions are useful, application or domain code should depend on the abstraction rather than a concrete persistence implementation.

Avoid circular dependencies between features.

For example, if a listing use case needs room or building information from Properties Service, it must use a verified integration contract or an appropriate abstraction. It must not directly access Properties Service's private JPA entities or database repositories.

Do not force every feature to use ports and adapters if doing so adds complexity without providing useful separation.

---

## 7. Feature Boundaries

Each feature owns its related business behavior and internal implementation.

Potential business areas include:

- `listing`: listing creation, retrieval, update, publication, closure, and discovery, subject to actual requirements.
- `rentalrequest`: rental requests associated with listings.
- `appointment`: appointments associated with the relevant rental workflow.
- `roommate/post`: roommate posts.
- `roommate/application`: applications associated with roommate posts.
- `integration/property`: communication with Properties Service, if required.
- `event`: shared event contracts or event infrastructure where a distinct package is justified.

The actual feature list must be determined by inspecting:

- Existing source code
- Flyway migration files
- Existing API endpoints
- Project requirements or SRS
- Contracts from related services

Do not infer complete business behavior from table names alone.

### Feature interaction rules

- Avoid importing another feature's persistence entities into API responses.
- Avoid accessing another feature's internal repositories directly.
- Prefer explicit application interfaces for cross-feature operations when needed.
- Avoid duplicating business rules across features.
- Avoid creating a separate feature for every entity if several entities form one cohesive business capability.
- Keep common utilities small and genuinely reusable.
- Preserve the ownership boundaries of other microservices.

### Listing and Properties Service boundary

Listing Service and Properties Service are separate services.

Before implementing a workflow that depends on room or building information:

1. Inspect the current MSS301 architecture and requirements.
2. Identify the authoritative owner of the relevant data.
3. Verify the API or event contract provided by that service.
4. Determine which fields Listing Service must store as snapshots, if any.
5. Determine how source data changes are propagated.
6. Define error handling for missing, unavailable, or invalid source data.

Do not assume that Listing Service can directly read the Properties Service database.

Do not invent endpoints, event names, payloads, or authentication mechanisms.

Do not trust a frontend-supplied building name, room number, price, address, or status as authoritative when the business workflow requires validation against the source service.

Do not implement synchronization through Kafka or HTTP until the required contract and behavior have been confirmed.

---

## 8. Shared Components

Recommended package:

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
- Reuse existing shared components when appropriate.
- Do not introduce a new response envelope if the project already has an established API response format.

---

## 9. Exception Handling and API Responses

Use a consistent error-handling strategy across the service.

Where appropriate:

- Validate HTTP requests at the API boundary.
- Use domain or application exceptions for business failures.
- Translate exceptions into HTTP responses at the API boundary.
- Use `@RestControllerAdvice` for centralized REST exception handling.
- Return stable error codes or messages if the current API contract supports them.
- Do not expose stack traces, SQL statements, database credentials, or internal implementation details to clients.

Do not replace an existing response envelope or error format without checking its consumers first.

Avoid catching exceptions only to log and suppress them.

For cross-service failures, distinguish appropriately between:

- Invalid client input
- Missing domain resources
- Business rule violations
- Authentication or authorization failures
- Timeouts and network failures
- External service errors
- Unexpected internal failures

Use HTTP status codes consistent with the existing API contract and system-wide conventions.

Do not invent a new error format or status-code policy for an isolated feature without reviewing the existing implementation.

---

## 10. Database and Flyway Rules

The project uses PostgreSQL and Flyway.

Migration directory:

`src/main/resources/db/migration/`

### Required rules

- Use Flyway migrations to manage schema changes.
- Inspect all relevant existing migrations before modifying database-related code.
- Never edit an already-applied migration merely to change the historical schema definition.
- Create a new versioned migration for a required schema change.
- Preserve existing table names, column names, constraints, indexes, and relationships unless a change is explicitly required.
- Keep JPA mappings consistent with the actual database schema.
- Do not use Hibernate schema generation as a replacement for Flyway migrations.
- Preserve the existing database and schema configuration.
- Do not drop or recreate existing tables as a shortcut for resolving migration errors.
- Do not insert production-specific or environment-specific secrets into migration files.
- Do not reset Flyway history to bypass a migration error.
- Do not add speculative tables or columns based only on a package name or an assumed feature.
- Inspect the current Flyway version and migration history before adding new migrations.

Preserve the existing Hibernate schema-management behavior. If the application currently uses `ddl-auto: validate`, `none`, or another setting, do not change it without a clear and reviewed requirement.

### Migration naming

Follow the convention already used by the project.

For example, if existing migration files use names such as:

```text
V01__create_room_listing.sql
V02__create_rental_request.sql
V03__create_appointment.sql
```

continue using the established convention for new migrations.

The filenames above are illustrative. Do not rename existing migrations to match these examples.

Before adding a migration:

1. Inspect existing migration files.
2. Identify the current migration version.
3. Check the relevant Flyway history if database access is available and authorized.
4. Confirm the requested schema change.
5. Check constraints, indexes, relationships, and compatibility with existing data.
6. Verify that entity mappings agree with the final schema.

### Entity rules

- Map entities to the actual database tables and columns.
- Use explicit mappings when database naming differs from Java naming.
- Avoid exposing entities directly from controllers.
- Avoid eager loading large object graphs by default.
- Be careful with bidirectional relationships and JSON serialization.
- Use transactions for operations that must be atomic within the service's database.
- Avoid adding indexes or constraints without considering query patterns and existing schema requirements.

### ID generation

Do not assume that every ID uses database identity generation.

Before implementing an entity ID strategy:

1. Inspect the relevant Flyway migration.
2. Check whether the primary key uses an identity column, sequence, database default, or externally generated ID.
3. Inspect existing ID generation utilities or shared libraries.
4. Verify how other services represent and generate IDs.
5. Select a strategy compatible with the actual database schema and system contract.

If an ID is a Snowflake-generated value stored as PostgreSQL `BIGINT`, do not replace it with `IDENTITY` unless a deliberate design change has been approved.

If the schema has no generation mechanism and the intended strategy is unclear, report the blocker rather than inventing one.

### Database ownership

Listing Service must use the database and schema configured for it.

Do not assume that the PostgreSQL database name and schema name are interchangeable.

Do not connect to another service's private database to avoid implementing an API or event integration.

---

## 11. Kafka and External Integrations

The project may contain Kafka dependencies, Kafka configuration, event-processing tables, or an outbox table.

Their presence does not automatically mean that every feature requires Kafka.

Do not introduce Kafka producers, consumers, event classes, or topic definitions merely because Kafka exists in the build configuration.

Before implementing an integration:

1. Inspect the existing Kafka configuration.
2. Identify the business event or integration requirement.
3. Identify the service that owns the event.
4. Verify the event contract and topic name.
5. Confirm the event payload and schema compatibility.
6. Determine retry, duplicate-delivery, idempotency, and failure-handling behavior.
7. Keep event publishing and consumption separate from controller logic.
8. Add tests for relevant success and failure cases.

### Processed events and outbox

If the existing schema contains processed-event or outbox-event tables:

- Inspect their existing purpose and implementation before changing them.
- Do not assume that an outbox pattern is already implemented just because an outbox table exists.
- Do not introduce a second event-processing mechanism without understanding the existing one.
- Preserve existing event contracts and idempotency behavior.
- Ensure that database changes and event-publishing behavior are designed consistently when atomic event publication is required.

If an integration does not exist yet, implement it only when required by the project.

### External service integration

When integrating with Properties Service or another service:

- Follow the actual service contract.
- Reuse existing HTTP clients or messaging conventions where appropriate.
- Do not invent API paths or event payloads.
- Do not hardcode service URLs or credentials.
- Use the existing configuration mechanism.
- Handle timeouts and failures appropriately.
- Do not expose sensitive internal error details to API clients.

---

## 12. Configuration and Security

Keep environment-specific configuration outside business logic.

Rules:

- Use the existing `application.yml` or `application.yaml` conventions.
- Use environment variables or the existing secret-management mechanism for sensitive values.
- Never hardcode database passwords, API keys, tokens, or credentials.
- Do not commit real secrets.
- Avoid scattering configuration values throughout services.
- Use typed configuration properties when configuration is complex or shared.
- Keep configuration classes focused on infrastructure setup.
- Preserve existing database, Flyway, Kafka, Actuator, and service-discovery configuration unless a change is required.
- Do not add authentication or authorization mechanisms from Signify merely because they exist in that project.

Before changing security behavior:

1. Inspect the MSS301 system architecture.
2. Inspect the API Gateway and service-to-service trust model.
3. Review the Listing Service's existing security configuration.
4. Identify which authentication and authorization responsibilities belong to the gateway or downstream services.
5. Follow the verified system contract.

Do not assume that Listing Service must independently implement a particular JWT validation mechanism or API key scheme.

Do not bypass existing security controls to make local testing easier.

---

## 13. Logging and Observability

Use structured, useful logging.

Rules:

- Log meaningful application events and unexpected failures.
- Avoid logging passwords, tokens, secrets, or sensitive personal information.
- Avoid logging the same exception repeatedly at every layer.
- Do not use `System.out.println()` as a replacement for application logging.
- Preserve existing Actuator and health-check configuration.
- Avoid introducing expensive logging or monitoring dependencies without justification.

Use appropriate log levels:

- `DEBUG`: diagnostic details.
- `INFO`: meaningful lifecycle or business events.
- `WARN`: recoverable abnormal situations.
- `ERROR`: unexpected failures that require investigation.

Avoid logging entire request or response bodies when they may contain sensitive information.

Do not claim that monitoring, health checks, or tracing are implemented unless the relevant configuration and behavior have been verified.

---

## 14. Testing Strategy

Tests should reflect the architecture and the actual behavior of each feature.

### Unit tests

Test business rules and application behavior without starting the entire application where practical.

### Web/API tests

Test:

- Request validation
- Response structures
- HTTP status codes
- Exception handling
- Relevant authorization behavior, where implemented

### Persistence tests

Test JPA mappings and repository queries against an appropriate test database or supported integration-test environment.

Check compatibility with PostgreSQL-specific features when relevant.

### Integration tests

Test important interactions among the application, database, Kafka, or external services when required.

Do not require every test to start all infrastructure if a focused unit or slice test is sufficient.

### Test conventions

- Keep tests under `src/test/java` using the corresponding package structure.
- Use meaningful test names.
- Test successful and unsuccessful cases.
- Test validation failures and important business rules.
- Test transaction and idempotency behavior where relevant.
- Avoid tests that only verify trivial getters or setters.
- Do not delete or weaken existing tests just to make the build pass.
- Do not claim tests passed unless they were actually executed successfully.

Prefer testing observable behavior rather than private implementation details.

---

## 15. Naming Conventions

Use consistent Java naming conventions.

| Component | Naming example |
|---|---|
| Controller | `ListingController` |
| Application service | `ListingService` |
| Request DTO | `CreateListingRequest` |
| Response DTO | `ListingResponse` |
| JPA entity | `RoomListingEntity`, if consistent with existing conventions |
| Spring Data repository | `RoomListingJpaRepository` |
| Domain repository interface | `RoomListingRepository` |
| Mapper | `ListingMapper` |
| Configuration | `KafkaConfig` |
| Business exception | `ListingNotFoundException` |

These are examples. Preserve existing conventions if changing names would create unnecessary churn.

Class names should describe their responsibility rather than their implementation history.

Avoid vague names such as `CommonService`, `DataManager`, `Helper`, and `BaseHandler` unless their purpose is genuinely clear.

Use names consistent with the existing domain model and API contract.

---

## 16. Code Quality Rules

- Follow the Java version declared by the project.
- Follow existing Maven and Spring Boot conventions.
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
- Do not change the Spring Boot, Spring Cloud, or Java versions without a clear requirement.
- Do not replace existing dependencies with alternatives simply because another library is familiar.

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
2. Read `AGENTS.md` or other applicable agent instructions if they exist.
3. Inspect the current source tree.
4. Inspect `pom.xml`.
5. Inspect `application.yml` or `application.yaml` and related configuration.
6. Inspect the relevant Flyway migration files.
7. Inspect existing entities, repositories, controllers, services, mappers, and tests.
8. Identify the requested feature and its current implementation.
9. Review relevant API contracts, SRS documents, and integration contracts when available.
10. Explain intended changes before performing a large refactor.

### When implementing a new feature

The AI agent must:

1. Identify the actual business requirement.
2. Determine whether the feature already exists under another package or name.
3. Follow the established feature-based package organization.
4. Add only the layers necessary for the feature.
5. Reuse existing shared components where appropriate.
6. Preserve API and database compatibility.
7. Add or update tests.
8. Run the relevant Maven build and tests.
9. Report the files changed, design decisions, and actual verification results.

Use the Maven Wrapper (`mvnw` or `mvnw.cmd`) if it exists and is the established project convention.

For example, on Windows:

```powershell
.\mvnw.cmd test
```

Or, if no Maven Wrapper exists and Maven is installed:

```powershell
mvn test
```

Run the appropriate build command based on the current project and requested scope. Do not claim that a command succeeded unless it actually completed successfully.

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

- Copy Signify-specific business modules into Listing Service.
- Replace Flyway with Liquibase.
- Replace Maven with Gradle.
- Replace PostgreSQL or change the database schema without an explicit requirement.
- Delete migrations or reset migration history to avoid resolving an error.
- Modify an already-applied Flyway migration to introduce a new schema change.
- Generate speculative business logic from package names or table names alone.
- Create unnecessary layers for trivial CRUD operations.
- Introduce a new dependency without justification.
- Claim a build or test succeeded when it was not executed.
- Change unrelated features during a targeted task.
- Invent undocumented integration contracts.
- Assume ID generation behavior without checking the actual schema and existing implementation.

---

## 19. Incremental Architecture Migration

If the current project does not yet follow this architecture, migrate incrementally.

Recommended order:

1. Inspect and document the existing architecture.
2. Identify the current business features and their dependencies.
3. Review Flyway migrations and existing entity mappings.
4. Establish consistent feature boundaries.
5. Refactor one feature at a time.
6. Separate controller responsibilities from application logic.
7. Introduce DTOs and mappers where needed.
8. Isolate persistence details where doing so provides meaningful value.
9. Extract genuinely shared components into `common/`.
10. Verify external integration boundaries.
11. Update and execute tests after each significant change.
12. Verify the complete Maven build before considering the migration complete.

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
- Relevant Maven build and test checks have been executed.
- No secrets or unnecessary dependencies have been introduced.
- No unrelated business logic has been changed.
- External integration contracts have been verified where applicable.
- Any limitations, unresolved assumptions, or failed checks are explicitly reported.

## Final Principle
The objective is to achieve architectural consistency and maintainability without copying unrelated functionality or introducing unnecessary complexity.