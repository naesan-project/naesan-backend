CREATE TABLE evidence_snapshots (
    id UUID PRIMARY KEY,
    evidence_id UUID NOT NULL REFERENCES purchase_evidence(id),
    schema_version INTEGER NOT NULL,
    canonical_payload BYTEA NOT NULL,
    snapshot_digest CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT evidence_snapshots_evidence_unique UNIQUE (evidence_id),
    CONSTRAINT evidence_snapshots_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT evidence_snapshots_payload_not_empty CHECK (
        OCTET_LENGTH(canonical_payload) > 0
    ),
    CONSTRAINT evidence_snapshots_digest_valid CHECK (
        snapshot_digest ~ '^[0-9a-f]{64}$'
    )
);
