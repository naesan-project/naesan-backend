package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    @DisplayName("외부 증명 요청 event는 즉시 처리 가능한 PENDING 상태로 생성된다")
    void createsPendingProofAnchorRequest() {
        Instant createdAt = Instant.parse("2026-07-18T00:00:00Z");

        OutboxEvent event = OutboxEvent.createProofAnchorRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "{\"schemaVersion\":1,\"commitment\":\"abc\"}",
                "proof-anchor:test",
                createdAt
        );

        assertThat(event.eventType()).isEqualTo(OutboxEvent.PROOF_ANCHOR_REQUESTED);
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.attemptCount()).isZero();
        assertThat(event.nextAttemptAt()).isEqualTo(createdAt);
    }
}
