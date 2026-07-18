package com.naesan.passport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    @Test
    @DisplayName("시도 횟수에 따라 bounded jitter가 적용된 exponential delay를 만든다")
    void createsBoundedExponentialDelay() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(
                5,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                new Random(1)
        );

        OutboxRetryDecision firstRetry = policy.decide(1);
        OutboxRetryDecision fourthRetry = policy.decide(4);

        assertThat(firstRetry.retryAllowed()).isTrue();
        assertThat(firstRetry.delay())
                .isBetween(Duration.ofMillis(500), Duration.ofMillis(1500));
        assertThat(fourthRetry.delay())
                .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("최대 시도 횟수에 도달하면 retry를 종료한다")
    void stopsAtMaximumAttempts() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(
                3,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                new Random(1)
        );

        OutboxRetryDecision decision = policy.decide(3);

        assertThat(decision.retryAllowed()).isFalse();
        assertThat(decision.delay()).isZero();
    }
}
