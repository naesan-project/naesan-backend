package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxReprocessContractTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final Instant REPROCESSED_AT = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    @DisplayName("dead letter event는 동일한 외부 명령을 새 제출 대기로 되돌린다")
    void reprocessesDeadLetterForSubmission() {
        OutboxEvent deadLetter = event(OutboxEventStatus.DEAD_LETTER, 5);

        OutboxEvent reprocessed = deadLetter.reprocess(REPROCESSED_AT);

        assertThat(reprocessed.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(reprocessed.attemptCount()).isZero();
        assertThat(reprocessed.nextAttemptAt()).isEqualTo(REPROCESSED_AT);
        assertThat(reprocessed.payload()).isEqualTo(deadLetter.payload());
        assertThat(reprocessed.dispatchKey()).isEqualTo(deadLetter.dispatchKey());
        assertThat(reprocessed.proofAnchorId()).isEqualTo(deadLetter.proofAnchorId());
    }

    @Test
    @DisplayName("manual review event는 자동 제출하지 않고 대사 대기로 되돌린다")
    void reprocessesManualReviewForReconciliation() {
        OutboxEvent manualReview = event(OutboxEventStatus.MANUAL_REVIEW, 2);

        OutboxEvent reprocessed = manualReview.reprocess(REPROCESSED_AT);

        assertThat(reprocessed.status())
                .isEqualTo(OutboxEventStatus.RECONCILE_PENDING);
        assertThat(reprocessed.attemptCount()).isZero();
    }

    @Test
    @DisplayName("진행 가능한 event는 운영자가 재처리할 수 없다")
    void rejectsNonTerminalEvent() {
        OutboxEvent pending = event(OutboxEventStatus.PENDING, 0);

        assertThatThrownBy(() -> pending.reprocess(REPROCESSED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private OutboxEvent event(OutboxEventStatus status, int attemptCount) {
        return OutboxEvent.restore(
                UUID.randomUUID(),
                OutboxEvent.PROOF_ANCHOR_REQUESTED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "{\"schemaVersion\":1}",
                "proof-anchor:" + UUID.randomUUID(),
                status,
                attemptCount,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );
    }
}
