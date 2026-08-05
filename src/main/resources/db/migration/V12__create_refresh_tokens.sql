CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_token_hash_length CHECK (
        OCTET_LENGTH(token_hash) = 32
    ),
    CONSTRAINT refresh_tokens_expiry_after_issue CHECK (
        expires_at > issued_at
    ),
    CONSTRAINT refresh_tokens_consumed_after_issue CHECK (
        consumed_at IS NULL OR consumed_at >= issued_at
    ),
    CONSTRAINT refresh_tokens_revoked_after_issue CHECK (
        revoked_at IS NULL OR revoked_at >= issued_at
    )
);

CREATE INDEX refresh_tokens_account_id_index
    ON refresh_tokens (account_id);
