CREATE TABLE import_staging (
    id BIGSERIAL PRIMARY KEY,
    import_id UUID NOT NULL,
    job_execution_id BIGINT,
    line_number INT NOT NULL,
    external_id TEXT,
    full_name TEXT,
    email TEXT,
    country TEXT,
    plan TEXT,
    signup_date TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
