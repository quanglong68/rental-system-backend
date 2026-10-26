# Local and Deployment Configuration

## Local development

The project root `.env` file is used by Docker Compose. Do not commit it. Copy `.env.example` to `.env` only when creating a local environment for the first time, then set local database and storage credentials. The default database is `property_db`.

Start the main `rental-system` stack first so its shared Kafka broker is running, then start PostgreSQL/PostGIS and this service:

```powershell
docker compose up -d --build
```

When running the application directly on Windows, use the host addresses published by Compose: PostgreSQL `localhost:5433` and Kafka `localhost:9092`. Spring Boot does not automatically load `.env` into a process launched by `gradlew`. In PowerShell, load the local file into the current process, then run the service:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } | ForEach-Object {
    $propertyKey, $propertyValue = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($propertyKey.Trim(), $propertyValue.Trim(), 'Process')
}
.\gradlew.bat bootRun
```

When the service runs inside Compose, PostgreSQL is `postgres:5432` and the shared Kafka broker is `kafka:9092`; `docker-compose.yaml` sets the Kafka override for the container. The `rental-net` external network must already exist, which the main `rental-system` stack creates.

## HTTP endpoints

- Swagger UI: <http://localhost:8083/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8083/v3/api-docs>
- Actuator health: <http://localhost:8083/actuator/health>
- Kubernetes-style liveness/readiness: `/actuator/health/liveness` and `/actuator/health/readiness`

Snowflake IDs in API responses, including nested references and paginated results, are JSON strings. Response DTO properties remain Java `Long` and use Jackson's per-property `ToStringSerializer`. Request body IDs are documented as decimal strings and bound to Java `Long`; URL path and query IDs are also accepted as decimal strings. IDs must be positive and within the signed 64-bit range. Clients should keep IDs as strings; prices, counts, coordinates, and pagination values remain numeric.

Swagger UI and OpenAPI JSON default to disabled in the base application configuration. Docker Compose enables them by default for local development; set `SWAGGER_UI_ENABLED=false` and `OPENAPI_ENABLED=false` in `.env` when running the Compose stack in a deployment. For a host-run local process, set both variables to `true` if the docs are needed. This service currently has no Spring Security integration; access control should be handled by the deployment/network boundary until authentication is implemented separately.

Actuator exposes only `health` and `info` by default. Health component details are hidden. Kafka's health contributor is disabled so a temporary broker outage does not by itself mark the HTTP service unhealthy. Database health remains part of the standard health result.

## Kafka setup

The service stores `RoomStatusChanged`, `RoomPriceChanged`, and `BuildingUpdated` in `outbox_event` within the business transaction. A relay claims one row at a time, sends it with the room or building ID as key, waits for broker acknowledgement, and only then marks it published. Failed sends remain retryable. The scheduled price job queues future `RoomPriceChanged` events when the price becomes effective at 00:05 Vietnam time.

The producer uses string keys and JSON string values, `acks=all`, and Kafka idempotence. Snowflake ID fields in property event payloads are JSON strings; the contract listener accepts both string IDs and legacy integral JSON IDs. The `contract.events` listener processes `ContractSigned`, `MoveOutApproved`, and `ContractEnded`. Successful processing and insertion into `processed_event` share one database transaction. Malformed envelopes go directly to `contract.events.DLT`; other failures receive three retries, one second apart. A DLT publish must be confirmed by the broker before the source record is recovered.

The service Compose file does not run Kafka or initialize topics. The main `rental-system` stack owns the shared broker and initializes `property.events`, `contract.events`, `property.events.DLT`, and `contract.events.DLT` with three partitions and replication factor one. The application does not auto-create topics.

Useful local checks:

```powershell
docker compose ps
docker compose -f ..\fdepartment-backend\rental-system-backend\infra\docker-compose.yml logs kafka --tail 100
docker compose -f ..\fdepartment-backend\rental-system-backend\infra\docker-compose.yml exec kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:9092
docker compose -f ..\fdepartment-backend\rental-system-backend\infra\docker-compose.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic property.events
docker compose -f ..\fdepartment-backend\rental-system-backend\infra\docker-compose.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic contract.events
```

These checks verify broker readiness and topic configuration. The Kafka integration test exercises message delivery when Testcontainers can reach the broker from the host.

## Environment variables

| Variable | Default/example | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | HTTP server port |
| `SHUTDOWN_TIMEOUT` | `20s` | Graceful shutdown wait |
| `DB_HOST`, `DB_PORT` | `localhost`, `5433` | PostgreSQL host and host-published port |
| `DB_NAME`, `SCHEMA_NAME` | `property_db`, `properties_service` | Database and schema |
| `DB_USER`, `DB_PASSWORD` | `postgres`, local placeholder | Database credentials; inject real credentials through deployment secrets |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker address for a process running on the host |
| `KAFKA_DOCKER_BOOTSTRAP_SERVERS` | `kafka:9092` | Shared broker address passed by Compose to the service container |
| `KAFKA_CONSUMER_GROUP_ID` | `property-service` | Consumer group |
| `PROPERTY_EVENTS_TOPIC`, `CONTRACT_EVENTS_TOPIC` | `property.events`, `contract.events` | Outgoing and incoming topics |
| `KAFKA_CONSUMER_RETRY_INTERVAL_MS`, `KAFKA_CONSUMER_RETRY_COUNT` | `1000`, `3` | Listener retries before DLT |
| `KAFKA_SEND_TIMEOUT_SECONDS` | `10` | Broker acknowledgement timeout |
| `OUTBOX_POLL_DELAY_MS`, `OUTBOX_BATCH_SIZE`, `OUTBOX_LEASE_SECONDS`, `OUTBOX_RETRY_DELAY_SECONDS` | `1000`, `100`, `30`, `5` | Relay polling, claim lease and resend delay |
| `ROOM_PRICE_ANNOUNCEMENT_CRON` | `0 5 0 * * *` | Scheduled future price event, Vietnam time |
| `KAFKA_AUTO_OFFSET_RESET` | `earliest` | Start position when the group has no committed offset |
| `KAFKA_PRODUCER_RETRIES` | `10` | Producer retry count; idempotence is enabled |
| `OPENAPI_ENABLED`, `SWAGGER_UI_ENABLED` | `true` | Enable OpenAPI JSON and Swagger UI |
| `ACTUATOR_EXPOSED_ENDPOINTS` | `health,info` | Actuator web endpoint allowlist |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level |
| `EUREKA_CLIENT_ENABLED`, `EUREKA_SERVER_URL` | `false`, localhost URL | Optional service discovery |
| `SNOWFLAKE_GENERATOR_ID` | `0` | Unique Snowflake generator ID per service instance |

The Compose service receives `kafka:9092` for Kafka and `postgres:5432` for PostgreSQL. Do not pass host-side `localhost` addresses to containers: inside a container, `localhost` refers to that container itself. The shared Compose Kafka listener uses plaintext for local development only; configure broker TLS/SASL and inject credentials through deployment secrets before using a secured production cluster.

## Build and run

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
docker compose up -d --build
```

Use the project's Java 21 toolchain. Flyway remains enabled, Hibernate DDL generation remains disabled, and schema changes continue to be managed by the existing migrations.
