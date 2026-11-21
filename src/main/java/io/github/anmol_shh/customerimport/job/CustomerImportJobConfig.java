package io.github.anmol_shh.customerimport.job;

import io.github.anmol_shh.customerimport.ingest.CsvLineMapper;
import io.github.anmol_shh.customerimport.ingest.CsvRecord;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.separator.DefaultRecordSeparatorPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Spring Batch configuration for the customer CSV import job.
 *
 * <p>Configures a single-step chunk-oriented job that reads customer records from a CSV file
 * via {@link FlatFileItemReader} and inserts raw rows into {@code import_staging} via {@link JdbcBatchItemWriter}.
 *
 * <p>Verified by inspecting spring-batch-core-6.0.6 and spring-batch-infrastructure-6.0.6.
 */
@Configuration(proxyBeanMethods = false)
public class CustomerImportJobConfig {

    private static final String INSERT_STAGING_SQL = """
            INSERT INTO import_staging (
                import_id,
                job_execution_id,
                line_number,
                external_id,
                full_name,
                email,
                country,
                plan,
                signup_date
            ) VALUES (
                :import_id,
                :job_execution_id,
                :line_number,
                :external_id,
                :full_name,
                :email,
                :country,
                :plan,
                :signup_date
            )
            """;

    /**
     * Step-scoped CSV reader.
     *
     * <p>IMPORTANT: The return type is declared as {@code FlatFileItemReader<CsvRecord>} (the concrete class)
     * rather than {@code ItemReader<CsvRecord>}. StepBuilder checks whether the reader implements {@code ItemStream}
     * to register it automatically for lifecycle callbacks (open, update, close). If declared as {@code ItemReader},
     * the Spring proxy does not expose {@code ItemStream} by default, and {@code open()} is never called.
     * Verified by inspecting ChunkOrientedStepBuilder in spring-batch-core-6.0.6.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<CsvRecord> customerCsvReader(
            @Value("#{jobParameters['file.path']}") String filePath
    ) {
        FlatFileItemReader<CsvRecord> reader = new FlatFileItemReader<>(new CsvLineMapper());
        reader.setName("customerCsvReader");
        reader.setResource(new FileSystemResource(filePath));
        reader.setLinesToSkip(1);
        reader.setRecordSeparatorPolicy(new DefaultRecordSeparatorPolicy());
        return reader;
    }

    /**
     * Step-scoped JDBC batch writer inserting records into {@code import_staging}.
     *
     * <p>Converts the {@code import_id} string job parameter to {@link UUID} once at bean creation.
     * Uses named parameters matching staging columns and accesses {@link CsvRecord} via record accessors.
     * Verified by inspecting JdbcBatchItemWriterBuilder in spring-batch-infrastructure-6.0.6.
     */
    @Bean
    @StepScope
    public JdbcBatchItemWriter<CsvRecord> customerStagingWriter(
            DataSource dataSource,
            @Value("#{jobParameters['import_id']}") String importIdStr,
            @Value("#{stepExecution.jobExecution.id}") Long jobExecutionId
    ) {
        UUID importId = UUID.fromString(importIdStr);

        return new JdbcBatchItemWriterBuilder<CsvRecord>()
                .dataSource(dataSource)
                .sql(INSERT_STAGING_SQL)
                .itemSqlParameterSourceProvider(record -> new MapSqlParameterSource()
                        .addValue("import_id", importId)
                        .addValue("job_execution_id", jobExecutionId)
                        .addValue("line_number", record.lineNumber())
                        .addValue("external_id", record.externalId())
                        .addValue("full_name", record.fullName())
                        .addValue("email", record.email())
                        .addValue("country", record.country())
                        .addValue("plan", record.plan())
                        .addValue("signup_date", record.signupDate()))
                .build();
    }

    /**
     * Chunk-oriented step reading CSV records and writing to staging.
     *
     * <p>Uses {@code StepBuilder.chunk(int)} returning {@code ChunkOrientedStepBuilder}, with
     * {@code transactionManager(PlatformTransactionManager)} configured explicitly.
     * Note: {@code StepBuilder.chunk(int, PlatformTransactionManager)} was deprecated for removal in Batch 6.0.
     * Verified by inspecting StepBuilder in spring-batch-core-6.0.6.
     */
    @Bean
    public Step importCustomerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Value("${batch.chunk-size:10}") int chunkSize,
            FlatFileItemReader<CsvRecord> reader,
            JdbcBatchItemWriter<CsvRecord> writer
    ) {
        return new StepBuilder("importCustomerStep", jobRepository)
                .<CsvRecord, CsvRecord>chunk(chunkSize)
                .reader(reader)
                .writer(writer)
                .transactionManager(transactionManager)
                .build();
    }

    /**
     * Customer import batch job consisting of a single chunk-oriented step.
     *
     * <p>No RunIdIncrementer is used because imports are uniquely identified by {@code import_id}.
     * Verified by inspecting JobBuilder in spring-batch-core-6.0.6.
     */
    @Bean
    public Job importCustomerJob(JobRepository jobRepository, Step importCustomerStep) {
        return new JobBuilder("importCustomerJob", jobRepository)
                .start(importCustomerStep)
                .build();
    }
}
