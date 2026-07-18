package com.naesan.passport.application;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class OutboxRetryPolicy {
    private static final double MINIMUM_JITTER_MULTIPLIER = 0.5;
    private static final double JITTER_RANGE = 1.0;

    private final int maximumAttempts;
    private final Duration baseDelay;
    private final Duration maximumDelay;
    private final RandomGenerator random;

    public OutboxRetryPolicy(
            int maximumAttempts,
            Duration baseDelay,
            Duration maximumDelay,
            RandomGenerator random
    ) {
        if (maximumAttempts <= 0) {
            throw new IllegalArgumentException("최대 시도 횟수는 0보다 커야 합니다.");
        }
        if (baseDelay == null || baseDelay.isZero() || baseDelay.isNegative()) {
            throw new IllegalArgumentException("기본 retry 지연은 0보다 커야 합니다.");
        }
        if (maximumDelay == null || maximumDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("최대 retry 지연은 기본 지연 이상이어야 합니다.");
        }
        this.maximumAttempts = maximumAttempts;
        this.baseDelay = baseDelay;
        this.maximumDelay = maximumDelay;
        this.random = Objects.requireNonNull(random);
    }

    public OutboxRetryDecision decide(int attemptCount) {
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("시도 횟수는 0보다 커야 합니다.");
        }
        if (attemptCount >= maximumAttempts) {
            return OutboxRetryDecision.exhausted();
        }
        return OutboxRetryDecision.retryAfter(jitteredDelay(attemptCount));
    }

    private Duration jitteredDelay(int attemptCount) {
        long maximumDelayMillis = maximumDelay.toMillis();
        long exponentialDelayMillis = exponentialDelayMillis(
                attemptCount,
                maximumDelayMillis
        );
        double jitterMultiplier = MINIMUM_JITTER_MULTIPLIER
                + random.nextDouble() * JITTER_RANGE;
        long jitteredDelayMillis = Math.max(
                1,
                Math.round(exponentialDelayMillis * jitterMultiplier)
        );
        return Duration.ofMillis(Math.min(jitteredDelayMillis, maximumDelayMillis));
    }

    private long exponentialDelayMillis(
            int attemptCount,
            long maximumDelayMillis
    ) {
        long delayMillis = baseDelay.toMillis();
        for (int exponent = 1;
                exponent < attemptCount && delayMillis < maximumDelayMillis;
                exponent++) {
            delayMillis = Math.min(delayMillis * 2, maximumDelayMillis);
        }
        return delayMillis;
    }
}
