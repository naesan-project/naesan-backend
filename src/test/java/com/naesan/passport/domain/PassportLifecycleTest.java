package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PassportLifecycleTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    @DisplayName("Passport는 외부 증명과 무관하게 ACTIVE로 발급된다")
    void issuesActivePassportWithPreparedProof() {
        UUID passportId = UUID.randomUUID();
        UUID holderAccountId = UUID.randomUUID();
        Passport passport = Passport.issue(
                passportId,
                UUID.randomUUID(),
                holderAccountId,
                ISSUED_AT
        );
        AnchorCommitment commitment = new AnchorCommitment(
                1,
                new byte[32],
                new byte[32]
        );
        ProofAnchor proofAnchor = ProofAnchor.prepare(
                UUID.randomUUID(),
                passportId,
                commitment,
                ISSUED_AT
        );

        assertThat(passport.status()).isEqualTo(PassportStatus.ACTIVE);
        assertThat(proofAnchor.state()).isEqualTo(ProofAnchorState.PREPARED);
    }

    @Test
    @DisplayName("최초 발급 이력에는 이전 보유자가 없다")
    void recordsInitialOwnership() {
        UUID passportId = UUID.randomUUID();
        UUID holderAccountId = UUID.randomUUID();

        OwnershipHistory history = OwnershipHistory.recordIssuance(
                UUID.randomUUID(),
                passportId,
                holderAccountId,
                ISSUED_AT
        );

        assertThat(history.previousHolderAccountId()).isNull();
        assertThat(history.newHolderAccountId()).isEqualTo(holderAccountId);
        assertThat(history.reason()).isEqualTo(OwnershipChangeReason.ISSUED);
    }
}
