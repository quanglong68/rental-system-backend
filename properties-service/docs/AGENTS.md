# AI Agent Instructions — Properties Service

Before making any changes, read `ARCHITECTURE.md` and follow its architectural guidelines.

## Project identity

- Project: `properties-service`
- Base package: `fptu.semester8.mss301.propertiesservice`
- Java: 21
- Framework: Spring Boot
- Build: Gradle Kotlin DSL
- Database: PostgreSQL
- Migration tool: Flyway

## Mandatory rules

1. Follow Feature-based Architecture with clear API, Application, Domain, and Infrastructure responsibilities.
2. Treat Signify BE as an architectural reference only. Never copy unrelated Signify business features into this project.
3. Inspect the current source code before creating packages, classes, or features.
4. Preserve existing API contracts, business behavior, database schema, and Flyway migration history unless explicitly instructed otherwise.
5. Use the existing dependencies and conventions whenever appropriate.
6. Do not create unnecessary abstractions, speculative packages, or empty classes.
7. Add `.gitkeep` only when an intentionally empty directory needs to be tracked.
8. Add or update tests for meaningful behavior changes.
9. Run relevant Gradle build and test commands when possible.
10. Report actual changes and test results honestly.

## Before implementing a task

- Identify the affected feature and existing implementation.
- Inspect related controllers, services, DTOs, entities, repositories, configurations, and tests.
- Inspect relevant Flyway migrations before changing persistence behavior.
- Explain significant architectural changes before performing a large refactor.

## After implementing a task

- Review package boundaries and dependency direction.
- Verify that API contracts and database mappings remain consistent.
- Check for unnecessary code, duplicate logic, and accidental unrelated changes.
- Run the relevant tests and build.
- Summarize modified files, design decisions, test results, and unresolved issues.