ALTER TABLE outbox_events
    ADD COLUMN reprocess_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_reprocess_count_non_negative CHECK (
        reprocess_count >= 0
    );

CREATE TABLE outbox_reprocess_audit (
    id UUID PRIMARY KEY,
    outbox_event_id UUID NOT NULL REFERENCES outbox_events(id),
    proof_anchor_id UUID NOT NULL REFERENCES proof_anchors(id),
    operator_id VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    previous_attempt_count INTEGER NOT NULL,
    reprocess_number INTEGER NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outbox_reprocess_audit_operator_not_blank CHECK (
        BTRIM(operator_id) <> ''
    ),
    CONSTRAINT outbox_reprocess_audit_reason_not_blank CHECK (
        BTRIM(reason) <> ''
    ),
    CONSTRAINT outbox_reprocess_audit_previous_status_valid CHECK (
        previous_status IN ('DEAD_LETTER', 'MANUAL_REVIEW')
    ),
    CONSTRAINT outbox_reprocess_audit_new_status_valid CHECK (
        new_status IN ('PENDING', 'RECONCILE_PENDING')
    ),
    CONSTRAINT outbox_reprocess_audit_attempt_non_negative CHECK (
        previous_attempt_count >= 0
    ),
    CONSTRAINT outbox_reprocess_audit_number_positive CHECK (
        reprocess_number > 0
    ),
    CONSTRAINT outbox_reprocess_audit_event_number_unique UNIQUE (
        outbox_event_id,
        reprocess_number
    )
);

CREATE INDEX outbox_reprocess_audit_event_requested_index
    ON outbox_reprocess_audit (outbox_event_id, requested_at, id);
