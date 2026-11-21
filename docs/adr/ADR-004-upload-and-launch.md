# ADR-004: Upload Endpoint and Synchronous Launch

Status: Accepted
PR: 5

---

## Decisions

**Fixed stored filename (`upload.csv`)**
Storing every uploaded file under `{storage-dir}/{importId}/upload.csv` prevents path traversal vulnerabilities and filesystem injection by completely discarding client-provided filenames during storage.

**Configurable storage directory outside repository (`importer.storage-dir`)**
Externalizing file storage outside the project directory prevents polluting source control and allows independent disk quota management in deployment environments.

**`import_request` maps the public UUID to the Batch execution**
The `import_request` table maintains a durable audit log linking external client-facing import UUIDs to internal Spring Batch `job_execution_id` metadata.

**Synchronous job launch is temporary for Week 1**
Launching the job synchronously within the HTTP request simplifies initial integration testing and validation before migrating to asynchronous task execution in Week 2.

**No `@Transactional` annotation around the job launch**
Omitting `@Transactional` from the controller ensures Spring Batch's `ChunkOrientedStep` executes each chunk in an independent physical transaction via its internal `TransactionTemplate` (`PROPAGATION_REQUIRED`), allowing completed chunks to remain committed even when subsequent chunks fail.

**HTTP 200 response now, transitioning to HTTP 202 in Week 2**
Returning HTTP 200 with final execution status and row counts suits synchronous Week 1 execution, and will be replaced by HTTP 202 Accepted once asynchronous execution is introduced.

**Propagating `JobOperator.start` launch exceptions as HTTP 500**
Allowing unexpected launch exceptions from `JobOperator.start` to propagate as HTTP 500 leaves `import_request.job_execution_id` as null to explicitly distinguish job launch failures from job execution failures in Week 1.
