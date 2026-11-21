# CustomerImport

A high-performance CSV customer import service built with **Spring Boot 4.1.2** and **Spring Batch 6.0.6**.

## Prerequisites

- **Java 21** LTS
- **Apache Maven 3.9+**
- **Docker Desktop** (must be running for local Compose workflow and Testcontainers integration tests)

---

## Local Development Quickstart

The project uses Spring Boot's built-in Docker Compose support to manage PostgreSQL automatically during local runs.

1. Ensure Docker is running.
2. Start the application:
   ```bash
   mvn spring-boot:run
   ```
   Spring Boot automatically reads `compose.yaml` and starts a PostgreSQL 16.16 container mapped to host port **5433** (avoiding standard port 5432 collisions). Flyway automatically runs migrations `V1`, `V2`, and `V3`.
3. Check health:
   ```bash
   curl.exe http://localhost:8080/actuator/health
   ```

---

## Importing Customers

The service exposes a multipart upload endpoint at `POST /imports`.

### Windows PowerShell Example (`curl.exe`)
```powershell
curl.exe -X POST "http://localhost:8080/imports" `
  -F "file=@src/test/resources/fixtures/customers.csv"
```

### Bash Example (`curl`)
```bash
curl -X POST "http://localhost:8080/imports" \
  -F "file=@src/test/resources/fixtures/customers.csv"
```

### Expected Response (`200 OK`)
```json
{
  "importId": "9d73967f-5f40-4d63-9d68-b785553734c8",
  "status": "COMPLETED",
  "rowsStaged": 3
}
```
*(Note: In Week 1 the job launches synchronously and returns HTTP 200 with the completed/failed status. In Week 2, execution will transition to asynchronous processing returning HTTP 202 Accepted).*

---

## Running Integration Tests

Integration tests use **Testcontainers** with `@ServiceConnection` to spin up isolated PostgreSQL 16.16 containers automatically.

Ensure Docker is running, then execute:
```bash
mvn test
```

---

## Architecture Decision Records (ADRs)

Key architectural choices and trade-offs are documented in single-sentence rationales:
- [ADR-001: Technology Stack](docs/adr/ADR-001-tech-stack.md)
- [ADR-002: Schema Ownership and Migrations](docs/adr/ADR-002-schema-ownership.md)
- [ADR-003: Chunk Processing and Staging Ingest](docs/adr/ADR-003-chunk-processing.md)
- [ADR-004: Upload Endpoint and Synchronous Launch](docs/adr/ADR-004-upload-and-launch.md)
