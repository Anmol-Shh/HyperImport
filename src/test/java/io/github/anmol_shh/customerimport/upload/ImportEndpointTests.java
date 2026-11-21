package io.github.anmol_shh.customerimport.upload;

import io.github.anmol_shh.customerimport.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for {@link ImportController} against PostgreSQL via Testcontainers.
 *
 * <p>Validates file upload, fixed file storage, synchronous job execution, chunk rollback on failure,
 * path traversal protection, and input validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "batch.chunk-size=10")
@Import(TestcontainersConfig.class)
class ImportEndpointTests {

    private static @TempDir Path storageDir;

    @LocalServerPort
    private int port;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient restClient;

    @DynamicPropertySource
    static void configureStorageDir(DynamicPropertyRegistry registry) {
        registry.add("importer.storage-dir", () -> storageDir.toAbsolutePath().toString());
    }

    @BeforeEach
    void setUp() throws Exception {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        if (Files.exists(storageDir)) {
            try (var stream = Files.walk(storageDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(storageDir))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void happyPathUploadSucceedsWithCompletedStatusAndStagedRows() throws Exception {
        byte[] csvBytes;
        try (InputStream in = new ClassPathResource("fixtures/customers.csv").getInputStream()) {
            csvBytes = in.readAllBytes();
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(csvBytes, "customers.csv"));

        ResponseEntity<ImportResponse> response = restClient.post()
                .uri("/imports")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ImportResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportResponse responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.status()).isEqualTo("COMPLETED");
        assertThat(responseBody.rowsStaged()).isEqualTo(3L);

        UUID importId = responseBody.importId();

        // Verify import_request tracking table
        Map<String, Object> requestRow = jdbcTemplate.queryForMap(
                "SELECT original_filename, stored_path, job_execution_id FROM import_request WHERE import_id = ?",
                importId
        );
        assertThat(requestRow.get("original_filename")).isEqualTo("customers.csv");
        assertThat((String) requestRow.get("stored_path")).endsWith("upload.csv");

        Long jobExecutionId = (Long) requestRow.get("job_execution_id");
        assertThat(jobExecutionId).isNotNull();

        // Read writeCount through JobRepository using job_execution_id (verified by inspecting spring-batch-core-6.0.6)
        JobExecution jobExecution = jobRepository.getJobExecution(jobExecutionId);
        assertThat(jobExecution).isNotNull();
        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getWriteCount()).isEqualTo(3);
        assertThat(responseBody.rowsStaged()).isEqualTo(stepExecution.getWriteCount());

        // Verify parameters on JobExecution
        assertThat(jobExecution.getJobParameters().getParameter("import_id")).isNotNull();
        assertThat(jobExecution.getJobParameters().getParameter("import_id").identifying()).isTrue();
        assertThat(jobExecution.getJobParameters().getParameter("file.path")).isNotNull();
        assertThat(jobExecution.getJobParameters().getParameter("file.path").identifying()).isFalse();
    }

    @Test
    void failedJobWhenRowHasMalformedColumnCount() {
        // Generate 20 rows where data row 14 (inside second chunk of 10) is malformed with only 2 columns
        StringBuilder sb = new StringBuilder();
        sb.append("external_id,full_name,email,country,plan,signup_date\n");
        for (int i = 1; i <= 20; i++) {
            if (i == 14) {
                sb.append("CUST-014,MalformedRowMissingColumns\n");
            } else {
                sb.append(String.format("CUST-%03d,Customer %d,c%d@example.com,US,BASIC,2026-01-01\n", i, i, i));
            }
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(sb.toString().getBytes(), "malformed.csv"));

        ResponseEntity<ImportResponse> response = restClient.post()
                .uri("/imports")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ImportResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportResponse responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.status()).isEqualTo("FAILED");

        // Chunk 1 (10 rows) committed; Chunk 2 (rows 11-20) failed at row 14 and rolled back
        // Per ADR-003 Week 2 rule: staged rows from a failed import must be cleaned up or never promoted.
        assertThat(responseBody.rowsStaged()).isEqualTo(10L);

        UUID importId = responseBody.importId();
        Map<String, Object> requestRow = jdbcTemplate.queryForMap(
                "SELECT job_execution_id FROM import_request WHERE import_id = ?",
                importId
        );
        assertThat(requestRow.get("job_execution_id")).isNotNull();
    }

    @Test
    void pathTraversalAttemptStoresFileAtFixedPathSafely() throws Exception {
        String clientFilename = "../../evil.csv";
        String csvContent = "external_id,full_name,email,country,plan,signup_date\nCUST-999,Evil Tester,evil@test.com,US,FREE,2026-05-01\n";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(csvContent.getBytes(), clientFilename));

        ResponseEntity<ImportResponse> response = restClient.post()
                .uri("/imports")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ImportResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportResponse responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        UUID importId = responseBody.importId();

        // Stored file must exist at {storageDir}/{importId}/upload.csv
        Path expectedStoredFile = storageDir.resolve(importId.toString()).resolve("upload.csv");
        assertThat(Files.exists(expectedStoredFile)).isTrue();

        // Walk entire storage temp directory and assert the only regular file is {importId}/upload.csv
        try (var stream = Files.walk(storageDir)) {
            List<Path> regularFiles = stream.filter(Files::isRegularFile).toList();
            assertThat(regularFiles).containsExactly(expectedStoredFile);
        }

        // No file named evil.csv must exist in or outside storageDir
        Path evilOutside = storageDir.getParent().resolve("evil.csv");
        assertThat(Files.exists(evilOutside)).isFalse();

        // Database stores client's original filename as data
        String originalFilename = jdbcTemplate.queryForObject(
                "SELECT original_filename FROM import_request WHERE import_id = ?",
                String.class,
                importId
        );
        assertThat(originalFilename).isEqualTo(clientFilename);
    }

    @Test
    void validationRejectsEmptyOrMissingFileWithBadRequest() {
        // Empty file part
        MultiValueMap<String, Object> emptyBody = new LinkedMultiValueMap<>();
        emptyBody.add("file", new NamedByteArrayResource(new byte[0], "empty.csv"));

        ResponseEntity<Void> emptyResponse = restClient.post()
                .uri("/imports")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(emptyBody)
                .exchange((request, res) -> ResponseEntity.status(res.getStatusCode()).build());

        assertThat(emptyResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Missing file part
        MultiValueMap<String, Object> missingBody = new LinkedMultiValueMap<>();

        ResponseEntity<Void> missingResponse = restClient.post()
                .uri("/imports")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(missingBody)
                .exchange((request, res) -> ResponseEntity.status(res.getStatusCode()).build());

        assertThat(missingResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Helper resource providing custom filename to multipart form data.
     */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
