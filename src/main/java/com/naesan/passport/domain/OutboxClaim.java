package com.naesan.passport.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboxClaim(
        OutboxEvent event,
        UUID claimToken,
        long fencingVersion,
        Instant leaseUntil,
        String claimedBy,
        OutboxClaimReason reason
) {

    public OutboxClaim {
        if (event == null
                || claimToken == null
                || leaseUntil == null
                || reason == null) {
            throw new IllegalArgumentException("Outbox claim 필수 값은 null일 수 없습니다.");
        }
        if (event.status() != OutboxEventStatus.CLAIMED) {
            throw new IllegalArgumentException("Claim event는 CLAIMED 상태여야 합니다.");
        }
        if (fencingVersion <= 0) {
            throw new IllegalArgumentException("Fencing version은 0보다 커야 합니다.");
        }
        if (claimedBy == null || claimedBy.isBlank()) {
            throw new IllegalArgumentException("Claim worker ID는 비어 있을 수 없습니다.");
        }
    }
}
