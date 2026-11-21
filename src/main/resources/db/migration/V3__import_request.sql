CREATE TABLE import_request (
    import_id         UUID PRIMARY KEY,
    original_filename TEXT NOT NULL,
    stored_path       TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    job_execution_id  BIGINT
);
