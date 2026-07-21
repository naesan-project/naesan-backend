CREATE TABLE public_shares (
    id UUID PRIMARY KEY,
    passport_id UUID NOT NULL REFERENCES passports(id),
    token_hash BYTEA NOT NULL,
    capability VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT public_shares_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT public_shares_token_hash_length CHECK (
        OCTET_LENGTH(token_hash) = 32
    ),
    CONSTRAINT public_shares_capability_valid CHECK (
        capability IN ('SUMMARY', 'FILE_MATCH')
    ),
    CONSTRAINT public_shares_expiry_valid CHECK (
        expires_at > created_at
    ),
    CONSTRAINT public_shares_revocation_valid CHECK (
        revoked_at IS NULL OR revoked_at >= created_at
    )
);

CREATE UNIQUE INDEX public_shares_one_unrevoked_per_passport
    ON public_shares (passport_id)
    WHERE revoked_at IS NULL;
