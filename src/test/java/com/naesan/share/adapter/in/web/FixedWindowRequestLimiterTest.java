package com.naesan.share.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FixedWindowRequestLimiterTest {

    @Test
    @DisplayName("client별 window 요청 수를 넘으면 거부한다")
    void limitsRequestsPerClient() {
        FixedWindowRequestLimiter limiter =
                new FixedWindowRequestLimiter(2, Duration.ofMinutes(1));
        Instant requestedAt = Instant.parse("2026-07-21T00:00:00Z");

        assertThat(limiter.tryAcquire("client-a", requestedAt)).isTrue();
        assertThat(limiter.tryAcquire("client-a", requestedAt)).isTrue();
        assertThat(limiter.tryAcquire("client-a", requestedAt)).isFalse();
        assertThat(limiter.tryAcquire("client-b", requestedAt)).isTrue();
    }

    @Test
    @DisplayName("window가 지나면 같은 client의 요청 수를 초기화한다")
    void resetsExpiredWindow() {
        FixedWindowRequestLimiter limiter =
                new FixedWindowRequestLimiter(1, Duration.ofMinutes(1));
        Instant requestedAt = Instant.parse("2026-07-21T00:00:00Z");

        assertThat(limiter.tryAcquire("client", requestedAt)).isTrue();
        assertThat(limiter.tryAcquire("client", requestedAt.plusSeconds(59)))
                .isFalse();
        assertThat(limiter.tryAcquire("client", requestedAt.plusSeconds(60)))
                .isTrue();
    }
}
