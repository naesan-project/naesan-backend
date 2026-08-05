ALTER TABLE proof_anchors
    ADD COLUMN chain_id NUMERIC(78, 0),
    ADD COLUMN contract_address VARCHAR(42),
    ADD COLUMN transaction_hash VARCHAR(66),
    ADD COLUMN block_number NUMERIC(78, 0),
    ADD COLUMN block_hash VARCHAR(66),
    ADD COLUMN confirmation_count INTEGER,
    ADD COLUMN read_back_commitment BYTEA,
    ADD COLUMN chain_checked_at TIMESTAMPTZ,
    ADD CONSTRAINT proof_anchors_transaction_hash_unique UNIQUE (transaction_hash),
    ADD CONSTRAINT proof_anchors_evm_evidence_complete CHECK (
        (
            chain_id IS NULL
            AND contract_address IS NULL
            AND transaction_hash IS NULL
            AND block_number IS NULL
            AND block_hash IS NULL
            AND confirmation_count IS NULL
            AND read_back_commitment IS NULL
            AND chain_checked_at IS NULL
        )
        OR
        (
            chain_id IS NOT NULL
            AND contract_address IS NOT NULL
            AND transaction_hash IS NOT NULL
            AND block_number IS NOT NULL
            AND block_hash IS NOT NULL
            AND confirmation_count IS NOT NULL
            AND read_back_commitment IS NOT NULL
            AND chain_checked_at IS NOT NULL
        )
    ),
    ADD CONSTRAINT proof_anchors_evm_numbers_valid CHECK (
        chain_id > 0
        AND block_number >= 0
        AND confirmation_count > 0
    ),
    ADD CONSTRAINT proof_anchors_evm_commitment_length CHECK (
        read_back_commitment IS NULL
        OR OCTET_LENGTH(read_back_commitment) = 32
    ),
    ADD CONSTRAINT proof_anchors_evm_read_back_matches CHECK (
        read_back_commitment IS NULL
        OR read_back_commitment = commitment
    ),
    ADD CONSTRAINT proof_anchors_evm_reference_matches CHECK (
        transaction_hash IS NULL
        OR transaction_hash = external_reference
    );
