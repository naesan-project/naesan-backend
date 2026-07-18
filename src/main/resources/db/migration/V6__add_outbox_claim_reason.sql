ALTER TABLE outbox_events
    ADD COLUMN claim_reason VARCHAR(30);

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_claim_reason_valid CHECK (
        claim_reason IS NULL
        OR claim_reason IN ('SUBMISSION', 'RECONCILIATION')
    );

CREATE INDEX outbox_events_expired_lease_index
    ON outbox_events (lease_until, created_at)
    WHERE status = 'CLAIMED';
