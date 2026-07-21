package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxReprocessAuditTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    @DisplayName("재처리 감사에는 명령 payload 없이 전후 상태와 운영 근거만 남긴다")
    void createsAuditWithoutOutboxPayload() {
        OutboxEvent previousEvent = event(OutboxEventStatus.DEAD_LETTER, 5);
        OutboxEvent reprocessedEvent = previousEvent.reprocess(REQUESTED_AT);

        OutboxReprocessAudit audit = OutboxReprocessAudit.create(
                UUID.randomUUID(),
                previousEvent,
                reprocessedEvent,
                "operator-1",
                "provider 장애 복구",
                1,
                REQUESTED_AT
        );

        assertThat(audit.previousStatus()).isEqualTo(OutboxEventStatus.DEAD_LETTER);
        assertThat(audit.newStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(audit.previousAttemptCount()).isEqualTo(5);
        assertThat(audit.reprocessNumber()).isOne();
    }

    @Test
    @DisplayName("허용되지 않은 전후 상태는 감사 기록으로 만들 수 없다")
    void rejectsUnsupportedStatuses() {
        OutboxEvent pending = event(OutboxEventStatus.PENDING, 0);

        assertThatThrownBy(() -> OutboxReprocessAudit.create(
                UUID.randomUUID(),
                pending,
                pending,
                "operator-1",
                "잘못된 요청",
                1,
                REQUESTED_AT
        )).isInstanceOf(IllegalArgumentException.class);
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
