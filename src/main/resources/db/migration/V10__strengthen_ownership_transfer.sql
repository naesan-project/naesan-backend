ALTER TABLE ownership_history
    DROP CONSTRAINT ownership_history_issuance_valid;

ALTER TABLE ownership_history
    ADD CONSTRAINT ownership_history_holder_transition_valid CHECK (
        (
            reason = 'ISSUED'
            AND previous_holder_account_id IS NULL
        )
        OR
        (
            reason = 'TRANSFERRED'
            AND previous_holder_account_id IS NOT NULL
            AND previous_holder_account_id <> new_holder_account_id
        )
    );
