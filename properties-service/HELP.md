# Properties Service: developer guide

This guide helps developers configure, build, run, test, and troubleshoot the current `properties-service` repository. It describes code and settings checked in here. Supply local credentials and the shared infrastructure before running it.

## Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Getting started](#getting-started)
- [Project structure](#project-structure)
- [Build and packaging](#build-and-packaging)
- [Run the service](#run-the-service)
- [Configuration](#configuration)
- [Architecture and components](#architecture-and-components)
- [Use the API](#use-the-api)
- [Testing](#testing)
- [Debugging and troubleshooting](#debugging-and-troubleshooting)
- [FAQ](#faq)
- [Maintenance](#maintenance)
- [References](#references)

## Overview

This single-project Java 21 Spring Boot REST service manages rental property data: provinces, wards, buildings, rooms, room types, prices, occupants, and images. It uses PostgreSQL/PostGIS, Flyway migrations, Kafka events, and S3-compatible Cloudflare R2 storage. Eureka service discovery is configurable. `settings.gradle.kts` defines one root project, `properties-service`; no subprojects are declared.

## Requirements

| Component | Requirement and evidence |
| --- | --- |
| JDK | Java 21 toolchain in `build.gradle.kts`; use a JDK 21 installation locally. |
| Gradle | Included wrapper requests **9.8.1**. No global Gradle installation is needed. Initial download and dependency resolution need network access. |
| OS | Windows wrapper (`gradlew.bat`) and Unix wrapper (`gradlew`) are included. No narrower OS support policy is declared. |
| Database | PostgreSQL with PostGIS and `btree_gist`; Compose uses `postgis/postgis:16-3.4`. The migration role needs permission to create required extensions. |
| Messaging | Reachable Kafka broker and configured `property.events`, `contract.events`, and `contract.events.DLT` topics. This repository's Compose file does not create Kafka or its topics. |
| Storage | Reachable R2 endpoint, bucket, and access credentials. The S3 client is constructed during startup. |
| Development tools | Docker Engine with Compose for the container setup and Testcontainers integration tests. An IDE with Java/Gradle support, such as IntelliJ IDEA, Eclipse, or VS Code, is useful but optional. |

The Dockerfile builds with a Java 21 Gradle image but runs the repository wrapper, so its `gradle:8.14-jdk21` base tag does **not** change the build's Gradle 9.8.1 version. The runtime image uses Java 21.

## Getting started

1. Clone using your repository remote URL and enter the root:

   ```bash
   git clone <repository-url>
   cd properties-service
   ```

2. Check Java and the wrapper. On Windows PowerShell:

   ```powershell
   java -version
   .\gradlew.bat --version
   ```

   On Linux/macOS:

   ```bash
   java -version
   ./gradlew --version
   ```

   If executable permission was lost on Unix, run `chmod +x gradlew`. The wrapper downloads its Gradle distribution on first use. A build task then resolves dependencies from Maven Central.

3. Configure PostgreSQL/PostGIS, Kafka, and R2 as described below. For Compose, copy `.env.example` to the ignored `.env` and replace its placeholder credentials. The external network `rental-system_rental-net` and shared Kafka stack must already be available; they are not provided here. [docs/CONFIGURATION.md](docs/CONFIGURATION.md) gives background on the neighboring stack.

4. Run a [build](#build-and-packaging), [start the service](#run-the-service), and check [the API](#use-the-api).

Keep real credentials out of Git. `.gitignore` excludes `.env` and generated build output.

## Project structure

```text
properties-service/
├── build.gradle.kts, settings.gradle.kts  # Build, dependencies, root project
├── gradlew, gradlew.bat, gradle/wrapper/   # Gradle wrapper
├── Dockerfile, docker-compose.yaml        # Container build and local stack
├── .env.example                            # Compose variable template
├── docs/                                   # Architecture, configuration, API guides
├── scripts/import-vietnam-admin.ps1       # Optional data import/validation
└── src/
    ├── main/
    │   ├── java/fptu/semester8/mss301/propertiesservice/
    │   │   ├── PropertiesServiceApplication.java
    │   │   ├── common/id/                  # Snowflake IDs
    │   │   ├── config/                     # Kafka, security, OpenAPI, R2, clock
    │   │   └── features/                   # Business features
    │   └── resources/
    │       ├── application.yaml            # Spring settings
    │       └── db/migration/               # Flyway SQL and CSV data
    └── test/                               # JUnit tests and event fixtures
```

The existing feature packages are `province`, `ward`, `building`, `room`, `roomtype`, `roomprice`, `roomoccupant`, `imagestorage`, and `events`. Their implemented subpackages include `api`/DTO, `application`, `domain`, `infrastructure`, and `mapper` where needed. The tree in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) is partly a **target example**, so do not assume every class shown there exists. The two SQL backup files at the root are not normal startup inputs; startup uses Flyway's versioned migrations.

## Build and packaging

Run from the project root. On Linux/macOS, replace `.\gradlew.bat` with `./gradlew`.

```powershell
.\gradlew.bat build
.\gradlew.bat clean build
.\gradlew.bat bootJar
.\gradlew.bat build -x test
```

`build` includes tests, some of which require Docker. `build -x test` is a packaging shortcut, not a test result. The Spring Boot plugin's `bootJar` produces an executable JAR under `build/libs/` (with the current project name/version, `properties-service-0.0.1-SNAPSHOT.jar`). The Dockerfile copies that JAR to `/app/app.jar` and runs `java -jar app.jar`. `clean` removes generated build output; it does not alter a database.

## Run the service

The application entry point is `fptu.semester8.mss301.propertiesservice.PropertiesServiceApplication.main`. Spring listens on port **8083** by default. Start database, broker/topics, and storage access before the application.

### Host process

Set `DB_PASSWORD` and valid R2 values in the process environment. If using the supplied Compose database from the host, use `DB_HOST=localhost` and `DB_PORT=5433`: `application.yaml` itself defaults to **5432**. The default Kafka host address is `localhost:9092`. Set `EUREKA_CLIENT_ENABLED=false` for standalone development without a discovery server.

Spring Boot does not automatically read `.env` for `bootRun`. For the template's simple `KEY=value` lines, PowerShell can load the reviewed local file into the current process:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5433'
$env:EUREKA_CLIENT_ENABLED = 'false'
.\gradlew.bat bootRun
```

For quoted values or multiline secrets, use normal shell or IDE environment settings instead of this simple loader. On Linux/macOS, export equivalent variables and run `./gradlew bootRun`. After `bootJar`, the configured process can run `java -jar build/libs/properties-service-0.0.1-SNAPSHOT.jar`; inspect `build/libs/` if the version changes.

In an IDE, import the Gradle project with JDK 21 and run `PropertiesServiceApplication`. Supply the same environment variables in the run configuration; enable Lombok and MapStruct annotation processing for IDE compilation.

### Docker Compose

The supplied Compose file starts PostGIS and this service. It needs the external `rental-system_rental-net` network, shared Kafka broker/topics, and real R2 values in `.env`. Once these exist:

```powershell
docker compose up -d --build
docker compose ps
docker compose logs properties-service --tail 100
```

Compose publishes HTTP at `${SERVER_PORT:-8083}` and PostgreSQL at `${DB_PORT:-5433}`. Inside containers, the service uses `postgres:5432` and `kafka:9092`; container `localhost` does not mean the host. Compose enables Swagger/OpenAPI and disables Eureka by default.

## Configuration

`src/main/resources/application.yaml` defines Spring defaults. `.env.example` is a Compose template, not a complete catalog of settings.

| Variable | Current default / meaning |
| --- | --- |
| `SERVER_PORT` | `8083`, HTTP port. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `SCHEMA_NAME` | `localhost`, `5432`, `property_db`, `properties_service` in Spring. Compose publishes database port `5433` on the host. |
| `DB_USER`, `DB_PASSWORD` | `postgres`, **no password default**. Set `DB_PASSWORD`. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`; Compose passes `KAFKA_DOCKER_BOOTSTRAP_SERVERS` (default `kafka:9092`). |
| `PROPERTY_EVENTS_TOPIC`, `CONTRACT_EVENTS_TOPIC` | `property.events`, `contract.events`; the consumer's dead-letter topic uses `.DLT`. |
| `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` | Blank in Spring, required by Compose. Real values are needed for storage startup/operations. |
| `STORAGE_IMAGE_MAX_SIZE` | `5242880` bytes per image; Spring multipart file/request limit is 50 MB. |
| `OPENAPI_ENABLED`, `SWAGGER_UI_ENABLED` | `false` in Spring, `true` in Compose. |
| `EUREKA_CLIENT_ENABLED` | `true` in Spring, `false` in Compose. |
| `SNOWFLAKE_GENERATOR_ID` | `0`; use a unique generator ID for concurrent writing instances. |
| `BUILDING_SEARCH_MAX_RADIUS_METERS` | `50000`. |
| `ACTUATOR_EXPOSED_ENDPOINTS`, `LOGGING_LEVEL_ROOT` | `health,info` and `INFO`. |

See `application.yaml` for Kafka retries, consumer group, outbox polling, price schedule, storage URL, and shutdown overrides. Flyway is enabled at `classpath:db/migration`; Hibernate DDL generation is `none`. The first migration explicitly creates `properties_service`, `postgis`, and `btree_gist`, so changing `SCHEMA_NAME` alone does not make all migrations schema-independent. Use deployment secrets or the ignored local `.env` for credentials. Some prose defaults in [docs/CONFIGURATION.md](docs/CONFIGURATION.md) differ from current source; follow `application.yaml` and implementation for current behavior.

## Architecture and components

- REST controllers in `features/*/api` validate inputs and invoke feature services. DTOs and MapStruct mappers shape API data.
- Application services coordinate rules and persistence. Features use Spring Data JPA or JDBC as implemented; PostGIS supports building location search.
- `PropertyEventPublisher` saves outgoing events in `outbox_event` within the business transaction; `OutboxRelay` sends them to Kafka. `ContractEventListener` and `ContractEventProcessor` process incoming events with processed-event tracking and a dead-letter path. Occupant projection and scheduled refresh update occupant/room state; the price announcement job queues future price events.
- `R2ObjectStorage` and image services handle S3-compatible uploads and image records.
- `OccupantSecurityConfig` protects occupant read paths using trusted gateway headers `X-User-Id`, `X-User-Type`, and `X-User-Roles`. This service does not verify those headers independently: restrict direct backend access and let a trusted gateway set them. Those security rules are scoped to occupant paths; review network access for other routes separately.

## Use the API

The base path is `/api/v1`. The [API index](docs/api/README.md) links to request/response guides for provinces, wards, buildings, rooms, room types, prices, images, and read-only occupants. Responses use the project's `ApiResponse` envelope. Snowflake `Long` IDs are serialized as decimal **strings**; clients should preserve them as strings to avoid JavaScript precision loss. Path/query IDs must be positive signed 64-bit decimal values.

After startup, these read-only checks require no sample data:

```powershell
Invoke-RestMethod http://localhost:8083/actuator/health
Invoke-RestMethod http://localhost:8083/api/v1/provinces
```

For a write example, [province requests](docs/api/provinces.md) use JSON `code`, `name`, and optional `divisionType`; `POST /api/v1/provinces` changes the database, so use a disposable local database. Occupant reads need authorized trusted gateway identity headers. Image upload needs working R2. OpenAPI JSON (`/v3/api-docs`) and Swagger UI (`/swagger-ui/index.html`) work only when enabled. Actuator exposes `health` and `info` by default; health details are hidden.

`scripts/import-vietnam-admin.ps1` validates bundled CSV files without changing the database when run without `-Apply`. Its `-Apply` mode imports data; review its target and back up the database before using it. It is not part of normal startup.

## Testing

Tests use JUnit Platform/Jupiter, Spring Boot test support, Mockito, and Testcontainers. `PostgresIntegrationSupport` starts `postgis/postgis:17-3.5`; the Kafka flow test starts Kafka too. Docker must be reachable and images available for the integration suite. The test support class disables Kafka listener startup and Eureka. On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests 'fptu.semester8.mss301.propertiesservice.common.id.SnowflakeIdGeneratorTest'
```

Use `./gradlew` on Linux/macOS. Successful runs write HTML to `build/reports/tests/test/index.html` and XML to `build/test-results/test/`. These tests use containers rather than an external production database or broker.

## Debugging and troubleshooting

Debug `PropertiesServiceApplication.main` in an IDE with the same environment variables as `bootRun`. Set breakpoints in the controller, service, repository, or Kafka listener involved. Logs go to the console by default; no file appender is configured. For containers, use `docker compose logs properties-service`. Read the earliest root cause in startup stack traces. Current `application.yaml` enables Kafka health; database and Kafka health can affect `/actuator/health`.

| Problem | Possible cause | Solution |
| --- | --- | --- |
| JDK/toolchain error | Java 21 unavailable or IDE uses another JDK | Check `java -version` and the Gradle JVM/toolchain. |
| Wrapper/dependency resolution failure | Network/proxy, Maven Central, or unwritable cache | Check connectivity and Gradle user-home permissions. |
| Gradle daemon/native library error | Host policy blocks Gradle DLL loading | Inspect the first native error; use a permitted, writable `GRADLE_USER_HOME` and allow the native library in the development environment. |
| Integration test failure | Docker/Testcontainers unavailable or image pull fails | Start Docker, check container access, and inspect test reports. |
| Database login/connection failure | `DB_PASSWORD` missing or `5432`/`5433` mismatch | Check process variables, `docker compose ps`, credentials, and published port. |
| Flyway/PostGIS failure | Extension privilege or incompatible schema/history | Inspect the first migration error and role privileges; do not delete Flyway history to hide mismatches. |
| Kafka topic/listener failure | Broker/topics absent or wrong advertised address | Start shared broker, verify configured topics and matching DLT partitions, and check bootstrap address. |
| Compose external network missing | `rental-system_rental-net` absent | Start the stack owning it or supply an equivalent network/broker. |
| R2/S3 startup or upload failure | Invalid endpoint, credentials, bucket, or permissions | Set valid R2 values and verify endpoint/bucket access; `change-me` is a placeholder. |
| Occupant route returns 401/403 | Missing gateway headers or insufficient role | Call through the trusted gateway; inspect `OccupantSecurityConfig`. |
| Eureka connection warnings | Host default enables discovery | Start Eureka or set `EUREKA_CLIENT_ENABLED=false`. |
| JAR not found | `bootJar` not run or version changed | Run `bootJar` and inspect `build/libs/`. |

## FAQ

**Does `.env` configure `bootRun` automatically?** No. Compose reads `.env`; host processes need exported variables or IDE run settings.

**Can this Compose file run alone?** It starts the service and PostGIS, but needs an external network, Kafka broker/topics, and R2 values.

**Why are IDs JSON strings?** They preserve full 64-bit Snowflake precision for JavaScript clients.

**Does startup import province/ward CSVs?** The separate PowerShell script has an explicit `-Apply` import path. Do not assume it ran automatically.

## Maintenance

Keep Java 21, wrapper, Spring Boot/Cloud, and dependency versions compatible when upgrading. Review `build.gradle.kts`, wrapper properties, and Dockerfile together. Add schema changes as new Flyway migrations and test on a disposable database; do not casually edit applied migrations. When event payloads/topics change, review producers, consumers, DLT setup, fixtures, and integration tests. For API DTO or security changes, update the relevant `docs/api/` guide. Then run `test`, `bootJar`, and an HTTP health/API smoke check with the required infrastructure.

## References

- [Java 21 documentation](https://docs.oracle.com/en/java/javase/21/)
- [Gradle 9.8.1 user guide](https://docs.gradle.org/9.8.1/userguide/introduction.html)
- [Spring Boot reference](https://docs.spring.io/spring-boot/reference/)
- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/)
- [Spring for Apache Kafka reference](https://docs.spring.io/spring-kafka/reference/)
- [Flyway documentation](https://documentation.red-gate.com/fd/)
- [Testcontainers Java documentation](https://java.testcontainers.org/)
- [Project configuration notes](docs/CONFIGURATION.md) and [API index](docs/api/README.md)
