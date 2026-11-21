# ADR-003: Chunk Processing and Staging Ingest

Status: Accepted
PR: 4

---

## Decisions

**Chunk-oriented processing over tasklet processing**
Chunk-oriented processing divides large CSV datasets into discrete, manageable batches to bound in-memory heap usage rather than loading the entire file into memory at once.

**Chunk size controls (`batch.chunk-size`)**
The chunk size determines the number of items read and written per transaction, balancing database network roundtrips and write throughput against heap footprint.

**Transaction per chunk boundary**
Each chunk commits in its own database transaction so that in Spring Batch 6.0, `ChunkOrientedStep` executes transactions only for chunks containing items, resulting in exactly ceil(N / chunkSize) commits (e.g. 3 commits for 21 rows at chunk size 10) without an extra trailing empty commit.

**`ChunkOrientedStepBuilder` over deprecated `SimpleStepBuilder`**
`StepBuilder.chunk(int, PlatformTransactionManager)` was marked deprecated for removal in Spring Batch 6.0 in favor of `StepBuilder.chunk(int)` returning `ChunkOrientedStepBuilder` with the transaction manager configured explicitly via `.transactionManager()`.

**`@StepScope` on reader and writer beans**
Step-scoped proxying allows beans to access late-bound runtime step execution attributes (such as job parameters and job execution IDs) that only become available when the step actually executes.

**Concrete return type `FlatFileItemReader<CsvRecord>` on reader bean**
Declaring the concrete class ensures Spring Batch's step builder detects that the bean implements `ItemStream` and registers it for stream lifecycle callbacks (`open()`, `update()`, `close()`), which interface-based proxies can obscure.

**`JobOperator.start(Job, JobParameters)` over deprecated `JobLauncher`**
`JobLauncher` is deprecated in Spring Batch 6.0, whereas `JobOperator.start(Job, JobParameters)` provides a modern, non-deprecated API that synchronously launches the job and returns the `JobExecution` directly.

**No `RunIdIncrementer`**
Imports are uniquely identified and tracked by their identifying `import_id` parameter, making arbitrary sequential run IDs redundant and preventing accidental duplicate re-runs of the same import request.

**Failed import staging isolation rule**
Staged rows from a failed import must be cleaned up or permanently ignored by downstream processing so that incomplete or corrupt ingest batches are never promoted into domain tables.
