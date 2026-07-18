package com.naesan.passport.application;

import java.time.Duration;

public record OutboxRetryDecision(
        boolean retryAllowed,
        Duration delay
) {

    public OutboxRetryDecision {
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("Retry delay는 음수일 수 없습니다.");
        }
        if (!retryAllowed && !delay.isZero()) {
            throw new IllegalArgumentException("종료 결정에는 retry delay가 없어야 합니다.");
        }
    }

    public static OutboxRetryDecision retryAfter(Duration delay) {
        return new OutboxRetryDecision(true, delay);
    }

    public static OutboxRetryDecision exhausted() {
        return new OutboxRetryDecision(false, Duration.ZERO);
    }
}
