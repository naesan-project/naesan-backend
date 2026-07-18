CREATE TABLE purchase_evidence (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES accounts(id),
    merchant_name VARCHAR(200) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    serial_number VARCHAR(200),
    purchased_at DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT purchase_evidence_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT purchase_evidence_currency_valid CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT purchase_evidence_state_valid CHECK (
        state IN ('DRAFT', 'FILE_ATTACHED', 'CONFIRMED')
    ),
    CONSTRAINT purchase_evidence_confirmation_consistent CHECK (
        (state = 'CONFIRMED' AND confirmed_at IS NOT NULL)
        OR (state <> 'CONFIRMED' AND confirmed_at IS NULL)
    )
);

CREATE INDEX purchase_evidence_owner_created_index
    ON purchase_evidence (owner_account_id, created_at DESC);
