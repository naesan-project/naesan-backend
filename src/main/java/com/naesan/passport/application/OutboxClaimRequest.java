package com.naesan.passport.application;

import java.time.Duration;
import java.util.UUID;

public record OutboxClaimRequest(
        String workerId,
        UUID claimToken,
        Duration leaseDuration
) {

    public OutboxClaimRequest {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("Worker ID는 비어 있을 수 없습니다.");
        }
        if (claimToken == null) {
            throw new IllegalArgumentException("Claim token은 필수입니다.");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration은 0보다 커야 합니다.");
        }
    }
}
