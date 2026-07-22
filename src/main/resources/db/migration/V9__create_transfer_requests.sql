CREATE TABLE transfer_requests (
    id UUID PRIMARY KEY,
    passport_id UUID NOT NULL REFERENCES passports(id),
    requester_account_id UUID NOT NULL REFERENCES accounts(id),
    recipient_account_id UUID NOT NULL REFERENCES accounts(id),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transfer_requests_distinct_accounts CHECK (
        requester_account_id <> recipient_account_id
    ),
    CONSTRAINT transfer_requests_status_valid CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT transfer_requests_version_non_negative CHECK (version >= 0),
    CONSTRAINT transfer_requests_expiry_valid CHECK (expires_at > created_at),
    CONSTRAINT transfer_requests_updated_after_created CHECK (
        updated_at >= created_at
    )
);

CREATE UNIQUE INDEX transfer_requests_one_pending_per_passport
    ON transfer_requests (passport_id)
    WHERE status = 'PENDING';

CREATE INDEX transfer_requests_requester_created_index
    ON transfer_requests (requester_account_id, created_at DESC, id DESC);

CREATE INDEX transfer_requests_recipient_created_index
    ON transfer_requests (recipient_account_id, created_at DESC, id DESC);
