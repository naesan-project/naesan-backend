CREATE TABLE evidence_files (
    id UUID PRIMARY KEY,
    evidence_id UUID NOT NULL REFERENCES purchase_evidence(id),
    object_key VARCHAR(512) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT evidence_files_evidence_unique UNIQUE (evidence_id),
    CONSTRAINT evidence_files_object_key_unique UNIQUE (object_key),
    CONSTRAINT evidence_files_sha256_valid CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT evidence_files_media_type_valid CHECK (
        media_type IN ('image/jpeg', 'image/png', 'application/pdf')
    ),
    CONSTRAINT evidence_files_size_positive CHECK (size_bytes > 0),
    CONSTRAINT evidence_files_state_valid CHECK (
        state IN ('TEMPORARY', 'PROMOTED', 'DELETION_PENDING', 'DELETED')
    )
);

CREATE INDEX evidence_files_state_updated_index
    ON evidence_files (state, updated_at);
