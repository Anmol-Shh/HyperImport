package io.github.anmol_shh.customerimport.schema;

import io.github.anmol_shh.customerimport.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying Flyway schema migrations against a real PostgreSQL container.
 *
 * <p>Validates that:
 * <ul>
 *   <li>Flyway migrations apply cleanly (V1 and V2 both recorded as successful).</li>
 *   <li>All Spring Batch 6.0.6 metadata tables and sequences exist.</li>
 *   <li>Table {@code import_staging} exists with expected columns, data types, and nullability constraints.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class SchemaMigrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsApplySuccessfully() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank"
        );

        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).get("version")).isEqualTo("1");
        assertThat(rows.get(0).get("success")).isEqualTo(true);

        assertThat(rows.get(1).get("version")).isEqualTo("2");
        assertThat(rows.get(1).get("success")).isEqualTo(true);
    }

    @Test
    void batchMetadataTablesAndSequencesExist() {
        // Table and sequence names derived directly from org/springframework/batch/core/schema-postgresql.sql
        // inside spring-batch-core-6.0.6.jar (6 metadata tables and 3 sequences).
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class
        );

        assertThat(tables).contains(
                "batch_job_instance",
                "batch_job_execution",
                "batch_job_execution_params",
                "batch_step_execution",
                "batch_step_execution_context",
                "batch_job_execution_context"
        );

        List<String> sequences = jdbcTemplate.queryForList(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'",
                String.class
        );

        assertThat(sequences).contains(
                "batch_job_instance_seq",
                "batch_job_execution_seq",
                "batch_step_execution_seq"
        );
    }

    @Test
    void importStagingTableHasCorrectColumnsAndTypes() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = 'import_staging'"
        );

        assertThat(columns).isNotEmpty();

        Map<String, Map<String, Object>> columnsByName = columns.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (String) row.get("column_name"),
                        row -> row
                ));

        // Primary key and identifiers
        assertThat(columnsByName.get("id").get("data_type")).isEqualTo("bigint");
        assertThat(columnsByName.get("id").get("is_nullable")).isEqualTo("NO");

        assertThat(columnsByName.get("import_id").get("data_type")).isEqualTo("uuid");
        assertThat(columnsByName.get("import_id").get("is_nullable")).isEqualTo("NO");

        assertThat(columnsByName.get("job_execution_id").get("data_type")).isEqualTo("bigint");
        assertThat(columnsByName.get("job_execution_id").get("is_nullable")).isEqualTo("YES");

        assertThat(columnsByName.get("line_number").get("data_type")).isEqualTo("integer");
        assertThat(columnsByName.get("line_number").get("is_nullable")).isEqualTo("NO");

        // Raw CSV text fields
        assertThat(columnsByName.get("external_id").get("data_type")).isEqualTo("text");
        assertThat(columnsByName.get("full_name").get("data_type")).isEqualTo("text");
        assertThat(columnsByName.get("email").get("data_type")).isEqualTo("text");
        assertThat(columnsByName.get("country").get("data_type")).isEqualTo("text");
        assertThat(columnsByName.get("plan").get("data_type")).isEqualTo("text");
        assertThat(columnsByName.get("signup_date").get("data_type")).isEqualTo("text");

        // Timestamp
        assertThat(columnsByName.get("created_at").get("data_type")).isEqualTo("timestamp with time zone");
    }
}
