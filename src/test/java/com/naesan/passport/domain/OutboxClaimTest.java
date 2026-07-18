package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxClaimTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    @DisplayName("Claim은 token과 fencing version과 lease를 함께 보존한다")
    void createsClaimWithFencingIdentity() {
        UUID claimToken = UUID.randomUUID();
        Instant leaseUntil = CREATED_AT.plusSeconds(30);

        OutboxClaim claim = new OutboxClaim(
                claimedEvent(),
                claimToken,
                3,
                leaseUntil,
                "worker-1",
                OutboxClaimReason.SUBMISSION
        );

        assertThat(claim.claimToken()).isEqualTo(claimToken);
        assertThat(claim.fencingVersion()).isEqualTo(3);
        assertThat(claim.leaseUntil()).isEqualTo(leaseUntil);
    }

    @Test
    @DisplayName("CLAIMED가 아닌 event로 claim을 만들 수 없다")
    void rejectsUnclaimedEvent() {
        OutboxEvent pendingEvent = OutboxEvent.createProofAnchorRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "{\"schemaVersion\":1}",
                "proof-anchor:test",
                CREATED_AT
        );

        assertThatThrownBy(() -> new OutboxClaim(
                pendingEvent,
                UUID.randomUUID(),
                1,
                CREATED_AT.plusSeconds(30),
                "worker-1",
                OutboxClaimReason.SUBMISSION
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private OutboxEvent claimedEvent() {
        return OutboxEvent.restore(
                UUID.randomUUID(),
                OutboxEvent.PROOF_ANCHOR_REQUESTED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "{\"schemaVersion\":1}",
                "proof-anchor:test",
                OutboxEventStatus.CLAIMED,
                1,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );
    }
}
