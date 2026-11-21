package io.github.anmol_shh.customerimport.job;

import io.github.anmol_shh.customerimport.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the customer import batch job against PostgreSQL via Testcontainers.
 *
 * <p>Validates chunk-oriented processing, line number mapping, identifying job parameter flags,
 * and staging row isolation per {@code import_id}.
 * Verified by inspecting spring-batch-core-6.0.6.
 */
@SpringBootTest(properties = "batch.chunk-size=10")
@Import(TestcontainersConfig.class)
class CustomerImportJobTests {

    private static final Logger log = LoggerFactory.getLogger(CustomerImportJobTests.class);

    @Autowired
    JobOperator jobOperator;

    @Autowired
    Job importCustomerJob;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void normalRunWithPr3FixtureSucceedsAndStagesAllRows(@TempDir Path tempDir) throws Exception {
        UUID importId = UUID.randomUUID();
        Path csvFile = tempDir.resolve("customers.csv");
        try (InputStream in = new ClassPathResource("fixtures/customers.csv").getInputStream()) {
            Files.copy(in, csvFile, StandardCopyOption.REPLACE_EXISTING);
        }

        JobParameters params = new JobParametersBuilder()
                .addString("import_id", importId.toString(), true)
                .addString("file.path", csvFile.toAbsolutePath().toString(), false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(importCustomerJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getWriteCount()).isEqualTo(3);

        Integer stagedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_staging WHERE import_id = ?",
                Integer.class,
                importId
        );
        assertThat(stagedCount).isEqualTo(3);
        assertThat(stagedCount).isEqualTo((int) stepExecution.getWriteCount());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT line_number, external_id, full_name, email, country, plan, signup_date, job_execution_id, import_id " +
                        "FROM import_staging WHERE import_id = ? ORDER BY line_number",
                importId
        );

        assertThat(rows).hasSize(3);

        // Every row carries the right import_id and the job_execution_id of this run
        for (Map<String, Object> row : rows) {
            assertThat(row.get("import_id")).isEqualTo(importId);
            assertThat(row.get("job_execution_id")).isEqualTo(execution.getId());
        }

        // Row 1 (normal row)
        assertThat(rows.get(0).get("line_number")).isEqualTo(2);
        assertThat(rows.get(0).get("external_id")).isEqualTo("CUST-001");
        assertThat(rows.get(0).get("full_name")).isEqualTo("Alice Smith");

        // Row 2 (quoted comma row)
        assertThat(rows.get(1).get("line_number")).isEqualTo(3);
        assertThat(rows.get(1).get("external_id")).isEqualTo("CUST-002");
        assertThat(rows.get(1).get("full_name")).isEqualTo("Smith, Bob");

        // Row 3 (multiline quoted record)
        assertThat(rows.get(2).get("line_number")).isEqualTo(4);
        assertThat(rows.get(2).get("external_id")).isEqualTo("CUST-003");
        assertThat(rows.get(2).get("full_name")).isEqualTo("Charlie\nBrown");
    }

    @Test
    void jobParametersCarryIdentifyingAndNonIdentifyingFlags(@TempDir Path tempDir) throws Exception {
        UUID importId = UUID.randomUUID();
        Path csvFile = tempDir.resolve("empty.csv");
        Files.writeString(csvFile, "external_id,full_name,email,country,plan,signup_date\n");

        JobParameters params = new JobParametersBuilder()
                .addString("import_id", importId.toString(), true)
                .addString("file.path", csvFile.toAbsolutePath().toString(), false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(importCustomerJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Verified by inspecting JobParameter and JobParameters in spring-batch-core-6.0.6
        JobParameter<?> importIdParam = execution.getJobParameters().getParameter("import_id");
        assertThat(importIdParam).isNotNull();
        assertThat(importIdParam.identifying()).isTrue();
        assertThat(importIdParam.value()).isEqualTo(importId.toString());

        JobParameter<?> filePathParam = execution.getJobParameters().getParameter("file.path");
        assertThat(filePathParam).isNotNull();
        assertThat(filePathParam.identifying()).isFalse();
        assertThat(filePathParam.value()).isEqualTo(csvFile.toAbsolutePath().toString());
    }

    @Test
    void chunkBoundaryWith21DataRowsStagesAllRows(@TempDir Path tempDir) throws Exception {
        UUID importId = UUID.randomUUID();
        Path csvFile = tempDir.resolve("customers-21.csv");

        StringBuilder sb = new StringBuilder();
        sb.append("external_id,full_name,email,country,plan,signup_date\n");
        for (int i = 1; i <= 21; i++) {
            sb.append(String.format("CUST-%03d,Customer %d,cust%d@example.com,US,BASIC,2026-01-01\n", i, i, i));
        }
        Files.writeString(csvFile, sb.toString());

        JobParameters params = new JobParametersBuilder()
                .addString("import_id", importId.toString(), true)
                .addString("file.path", csvFile.toAbsolutePath().toString(), false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(importCustomerJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getWriteCount()).isEqualTo(21);

        Integer stagedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_staging WHERE import_id = ?",
                Integer.class,
                importId
        );
        assertThat(stagedCount).isEqualTo(21);

        // Log commitCount as instructed, without asserting on it
        long commitCount = stepExecution.getCommitCount();
        log.info("Step commitCount for 21 data rows with chunk-size 10: {}", commitCount);
    }

    @Test
    void chunkBoundaryWith20DataRowsStagesAllRows(@TempDir Path tempDir) throws Exception {
        UUID importId = UUID.randomUUID();
        Path csvFile = tempDir.resolve("customers-20.csv");

        StringBuilder sb = new StringBuilder();
        sb.append("external_id,full_name,email,country,plan,signup_date\n");
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format("CUST-%03d,Customer %d,cust%d@example.com,US,BASIC,2026-01-01\n", i, i, i));
        }
        Files.writeString(csvFile, sb.toString());

        JobParameters params = new JobParametersBuilder()
                .addString("import_id", importId.toString(), true)
                .addString("file.path", csvFile.toAbsolutePath().toString(), false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(importCustomerJob, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getWriteCount()).isEqualTo(20);

        Integer stagedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_staging WHERE import_id = ?",
                Integer.class,
                importId
        );
        assertThat(stagedCount).isEqualTo(20);
    }
}
