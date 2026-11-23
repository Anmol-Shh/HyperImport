package io.github.anmol_shh.customerimport.upload;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Controller handling CSV file upload and launching the customer import batch job.
 *
 * <p>IMPORTANT: Do NOT annotate this controller or any method wrapping the job launch with
 * {@code @Transactional}. While the {@link JobOperator} proxy includes a {@code TransactionInterceptor},
 * {@code JobOperator.start} has no transaction attribute and runs without an ambient transaction.
 * In {@code ChunkOrientedStep}, each chunk executes in its own {@code TransactionTemplate} with
 * {@code PROPAGATION_REQUIRED}, committing each chunk as an independent physical transaction.
 * An outer {@code @Transactional} on this controller would bind all chunks to a single outer transaction,
 * causing earlier chunks to roll back if a later chunk fails.
 */
@RestController
public class ImportController {

    private final ImportRequestRepository importRequestRepository;
    private final JobOperator jobOperator;
    private final Job importCustomerJob;
    private final JdbcTemplate jdbcTemplate;
    private final String storageDir;

    public ImportController(
            ImportRequestRepository importRequestRepository,
            JobOperator jobOperator,
            @Qualifier("importCustomerJob") Job importCustomerJob,
            JdbcTemplate jdbcTemplate,
            @Value("${importer.storage-dir}") String storageDir
    ) {
        this.importRequestRepository = importRequestRepository;
        this.jobOperator = jobOperator;
        this.importCustomerJob = importCustomerJob;
        this.jdbcTemplate = jdbcTemplate;
        this.storageDir = storageDir;
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> uploadCustomerCsv(
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UUID importId = UUID.randomUUID();

        // Save file under {storage-dir}/{importId}/upload.csv (FIXED filename to prevent path traversal)
        Path targetDir = Path.of(storageDir, importId.toString());
        Files.createDirectories(targetDir);
        Path storedPath = targetDir.resolve("upload.csv");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, storedPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Store client's original filename only as data in import_request
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        importRequestRepository.save(new ImportRequest(importId, originalFilename, storedPath.toString(), null));

        JobParameters params = new JobParametersBuilder()
                .addString("import_id", importId.toString(), true)
                .addString("file.path", storedPath.toAbsolutePath().toString(), false)
                .toJobParameters();

        // If jobOperator.start throws, let it propagate as HTTP 500 (import_request retains null job_execution_id)
        JobExecution execution = jobOperator.start(importCustomerJob, params);

        // Update job_execution_id whether job completed or failed
        importRequestRepository.updateJobExecutionId(importId, execution.getId());

        Long rowsStaged = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_staging WHERE import_id = ?",
                Long.class,
                importId
        );

        // Synchronous HTTP 200 response in Week 1; will transition to HTTP 202 Accepted in Week 2 (async launch)
        return ResponseEntity.ok(new ImportResponse(
                importId,
                execution.getStatus().name(),
                rowsStaged != null ? rowsStaged : 0L
        ));
    }
}
