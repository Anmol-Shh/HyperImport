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

   *Note on container lifecycle:* Spring Boot's Docker Compose support automatically stops the containers when the application process exits. To wipe all persisted container data and database volumes, run:
   ```bash
   docker compose down -v
   ```
3. Check health:
   ```bash
   curl.exe http://localhost:8080/actuator/health
   ```
4. Inspect `import_staging` data with `psql`:
   ```bash
   psql -h localhost -p 5433 -U postgres -d postgres -c "SELECT import_id, line_number, external_id, full_name, plan FROM import_staging;"
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

---

## Status and Known Limitations

This codebase reflects the completed **Week 1** foundation:
- **Week 1 Scope**: Focuses strictly on resilient ingest into the raw `import_staging` table.
- **Synchronous Launch**: The batch job launches synchronously via `JobOperator.start` within the request thread, returning HTTP 200 OK with execution status and staged row counts (asynchronous job launch returning HTTP 202 Accepted will be introduced in Week 2).
- **No Business Validation**: CSV fields are parsed and staged directly as raw text columns without domain validations, email checks, or plan constraints (validation and error reporting are scheduled for Week 2).
- **Committed Chunks on Failure**: Because chunks commit independently, a `FAILED` import (e.g. failing on row 14 of chunk 2) leaves its already-committed chunks (e.g. rows 1–10 of chunk 1) in `import_staging`. In Week 2, promotion to production tables will require an import status check or staging cleanup so rows from failed imports are never promoted.

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
