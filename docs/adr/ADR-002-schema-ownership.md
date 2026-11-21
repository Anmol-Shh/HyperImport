# ADR-002: Schema Ownership and Migrations

Status: Accepted
PR: 2

---

## Decisions

**Flyway owns all DDL (including Spring Batch metadata)**
Flyway manages and executes all schema migrations for both application tables and Spring Batch metadata tables to maintain an immutable, version-controlled audit trail across all environments.

**`spring.batch.jdbc.initialize-schema=never`**
Explicitly disabling Spring Batch's internal schema initialization prevents conflicts, duplicate creation attempts, or race conditions with Flyway-managed schema creation.

**`V1__batch_schema.sql` source path and immutability policy**
`V1__batch_schema.sql` was extracted byte-for-byte from `org/springframework/batch/core/schema-postgresql.sql` inside `spring-batch-core-6.0.6.jar` (`~/.m2/repository/org/springframework/batch/spring-batch-core/6.0.6/spring-batch-core-6.0.6.jar`) and must never be modified once merged; future Batch version upgrades will be applied by diffing the new release's DDL against V1 and adding a new migration (`V<N>__upgrade_batch_metadata.sql`).

**`import_staging.job_execution_id` has no foreign key to Batch metadata on purpose**
Batch metadata gets purged or archived on its own schedule, and a foreign key constraint would block that or force cascading deletes, while staging rows are application data with their own independent lifecycle.

**All CSV fields are raw `TEXT` on purpose**
Storing CSV fields as unvalidated `TEXT` ensures raw input is captured faithfully during initial ingest without premature type casting or conversion failures before downstream processing.

**No indexes on `import_staging` yet**
Secondary indexes are deliberately omitted during initial ingest to maximize write throughput, deferring index selection to the Week 3 benchmark phase where query access patterns can be measured.
