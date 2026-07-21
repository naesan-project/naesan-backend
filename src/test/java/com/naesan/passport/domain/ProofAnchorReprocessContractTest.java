package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProofAnchorReprocessContractTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final Instant REPROCESSED_AT = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    @DisplayName("수동 검토 proof는 제출이 아니라 대사를 재개한다")
    void resumesReconciliationFromManualReview() {
        ProofAnchor manualReview = proofAnchor(ProofAnchorState.MANUAL_REVIEW);

        ProofAnchor resumed = manualReview.resumeReconciliation(REPROCESSED_AT);

        assertThat(resumed.state()).isEqualTo(ProofAnchorState.RECONCILE_PENDING);
        assertThat(resumed.commitment()).isEqualTo(manualReview.commitment());
        assertThat(resumed.updatedAt()).isEqualTo(REPROCESSED_AT);
    }

    @Test
    @DisplayName("수동 검토 상태가 아니면 운영 대사를 재개할 수 없다")
    void rejectsProofOutsideManualReview() {
        ProofAnchor prepared = proofAnchor(ProofAnchorState.PREPARED);

        assertThatThrownBy(() -> prepared.resumeReconciliation(REPROCESSED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private ProofAnchor proofAnchor(ProofAnchorState state) {
        return ProofAnchor.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new byte[32],
                new byte[32],
                state,
                null,
                CREATED_AT,
                CREATED_AT
        );
    }
}
