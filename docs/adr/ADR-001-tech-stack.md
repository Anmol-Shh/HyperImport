# ADR-001: Technology Stack

Status: Accepted
PR: 1

---

## Decisions

**Spring Boot 4.1.2 + Spring Batch 6.0.6**
Boot 4 is the current stable release and manages Batch 6, which introduces the non-deprecated `JobOperator.start(Job, JobParameters)` API we depend on in PR 5.

**Java 21**
The current LTS release; Boot 4 requires Java 17 or later and 21 is the recommended version for new projects.

**PostgreSQL (postgres:16.16)**
Single relational store for staging and domain data; keeps the Week 1 scope simple. The exact patch version is pinned to avoid silent behaviour changes between test and production environments.

**Flyway for all schema management**
Flyway owns every DDL statement, including Batch metadata tables. This keeps schema history reviewable in source control and makes upgrades explicit. See ADR-002 for upgrade procedure.

**`spring.batch.jdbc.initialize-schema=never`**
Explicit setting ensures Batch never auto-creates its schema, even on embedded databases. Without this, the behaviour depends on the datasource type, which is confusing. Flyway handles all initialization.

**`spring.batch.job.enabled=false`**
Jobs launch on-demand via the REST endpoint (PR 5), not at application startup. This also prevents Boot from trying to run jobs during integration tests that only test infrastructure wiring.

**No JPA**
JPA adds an ORM layer that is not needed here. `JdbcTemplate` and `JdbcBatchItemWriter` give explicit SQL control, clearer transaction boundaries, and are easier to explain in an interview than Hibernate internals.

**No Lombok**
Lombok hides code generation that obscures what the compiler actually sees. Java records serve the same purpose for DTOs and have correct equality semantics without annotation processing.

**Testcontainers 2.0.5 for all integration tests**
H2 cannot run PostgreSQL-specific schema scripts (e.g. `uuid`, `timestamptz`, `bigserial`). Testcontainers runs the same Postgres version as local dev and production, eliminating "works on H2, fails on Postgres" failures.

**`@ServiceConnection` for datasource wiring**
Boot's `@ServiceConnection` reads the container's connection details automatically; no manual datasource property override is needed. This reduces test boilerplate and couples the test datasource directly to the container lifecycle.

**`postgres:16.16` pinned identically in `compose.yaml` and `TestcontainersConfig`**
Same image tag in both places ensures local dev (`spring-boot:run`) and test runs (`mvn test`) operate against identical Postgres behaviour. If the tag changes, both files must be updated together.

**`@EnableBatchProcessing` omitted**
In Spring Boot 4 / Spring Batch 6, Boot auto-configures `JobRepository`, `JobOperator`, `PlatformTransactionManager`, and related beans when `spring-batch-core` is on the classpath. Adding `@EnableBatchProcessing` overrides this auto-configuration and requires you to supply those beans manually, which is unnecessary complexity. Verified against the Spring Batch 6.0 migration guide.

**Boot Docker Compose support handles local dev**
`spring-boot:run` detects `compose.yaml` and starts it automatically. Running `docker compose up -d` separately before `spring-boot:run` is redundant and can cause port conflicts if the named volume or port 5432 is already bound. The README quickstart uses `mvn spring-boot:run` only.

**`spring-boot-starter-jdbc` explicitly included**
Spring Boot 4 unbundled JDBC into its own module and `spring-boot-starter-batch` no longer transitively includes it, so declaring it directly is required for `DataSourceAutoConfiguration` and the `JdbcContainerConnectionDetailsFactory` backing `@ServiceConnection`.
