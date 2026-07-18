CREATE TABLE passports (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES evidence_snapshots(id),
    current_holder_account_id UUID NOT NULL REFERENCES accounts(id),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT passports_snapshot_unique UNIQUE (snapshot_id),
    CONSTRAINT passports_status_valid CHECK (
        status IN ('ACTIVE', 'ARCHIVED', 'WITHDRAWN')
    ),
    CONSTRAINT passports_version_non_negative CHECK (version >= 0)
);

CREATE TABLE ownership_history (
    id UUID PRIMARY KEY,
    passport_id UUID NOT NULL REFERENCES passports(id),
    previous_holder_account_id UUID REFERENCES accounts(id),
    new_holder_account_id UUID NOT NULL REFERENCES accounts(id),
    reason VARCHAR(20) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ownership_history_reason_valid CHECK (
        reason IN ('ISSUED', 'TRANSFERRED')
    ),
    CONSTRAINT ownership_history_issuance_valid CHECK (
        reason <> 'ISSUED' OR previous_holder_account_id IS NULL
    )
);

CREATE INDEX ownership_history_passport_changed_index
    ON ownership_history (passport_id, changed_at, id);

CREATE TABLE proof_anchors (
    id UUID PRIMARY KEY,
    passport_id UUID NOT NULL REFERENCES passports(id),
    schema_version INTEGER NOT NULL,
    anchor_salt BYTEA,
    commitment BYTEA NOT NULL,
    state VARCHAR(30) NOT NULL,
    external_reference VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT proof_anchors_passport_unique UNIQUE (passport_id),
    CONSTRAINT proof_anchors_external_reference_unique UNIQUE (external_reference),
    CONSTRAINT proof_anchors_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT proof_anchors_salt_length CHECK (
        anchor_salt IS NULL OR OCTET_LENGTH(anchor_salt) = 32
    ),
    CONSTRAINT proof_anchors_commitment_length CHECK (
        OCTET_LENGTH(commitment) = 32
    ),
    CONSTRAINT proof_anchors_state_valid CHECK (
        state IN (
            'PREPARED',
            'SUBMITTED',
            'CONFIRMED',
            'UNKNOWN',
            'RECONCILE_PENDING',
            'MANUAL_REVIEW'
        )
    ),
    CONSTRAINT proof_anchors_updated_after_created CHECK (
        updated_at >= created_at
    )
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES passports(id),
    proof_anchor_id UUID NOT NULL REFERENCES proof_anchors(id),
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    dispatch_key VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    fencing_version BIGINT NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ,
    claimed_by VARCHAR(100),
    error_category VARCHAR(50),
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outbox_events_dispatch_key_unique UNIQUE (dispatch_key),
    CONSTRAINT outbox_events_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT outbox_events_payload_object CHECK (
        JSONB_TYPEOF(payload) = 'object'
    ),
    CONSTRAINT outbox_events_status_valid CHECK (
        status IN (
            'PENDING',
            'CLAIMED',
            'SUCCEEDED',
            'RETRY_WAIT',
            'RECONCILE_PENDING',
            'MANUAL_REVIEW',
            'DEAD_LETTER'
        )
    ),
    CONSTRAINT outbox_events_attempt_count_non_negative CHECK (attempt_count >= 0),
    CONSTRAINT outbox_events_fencing_version_non_negative CHECK (fencing_version >= 0),
    CONSTRAINT outbox_events_updated_after_created CHECK (updated_at >= created_at)
);

CREATE INDEX outbox_events_due_index
    ON outbox_events (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_WAIT');
