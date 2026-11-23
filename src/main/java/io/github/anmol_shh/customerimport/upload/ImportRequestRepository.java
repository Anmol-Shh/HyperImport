package io.github.anmol_shh.customerimport.upload;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for managing {@code import_request} tracking records using JdbcTemplate.
 */
@Repository
public class ImportRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImportRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ImportRequest request) {
        jdbcTemplate.update(
                "INSERT INTO import_request (import_id, original_filename, stored_path, job_execution_id) VALUES (?, ?, ?, ?)",
                request.importId(),
                request.originalFilename(),
                request.storedPath(),
                request.jobExecutionId()
        );
    }

    public void updateJobExecutionId(UUID importId, long jobExecutionId) {
        jdbcTemplate.update(
                "UPDATE import_request SET job_execution_id = ? WHERE import_id = ?",
                jobExecutionId,
                importId
        );
    }
}
