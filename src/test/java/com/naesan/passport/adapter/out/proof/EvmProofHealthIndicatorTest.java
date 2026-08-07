package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.application.port.out.ProofProviderTelemetry;

class EvmProofHealthIndicatorTest {
    private static final Instant CHECKED_AT = Instant.parse("2026-08-07T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CHECKED_AT, ZoneOffset.UTC);
    private static final ProofProviderTelemetry NOOP_TELEMETRY =
            new ProofProviderTelemetry() {
                @Override
                public void recordProbe(ProbeStatus status, Duration duration) {
                }

                @Override
                public void recordRecovery(Duration outageDuration) {
                }
            };

    @Test
    @DisplayName("첫 RPC 검증 전에는 Web3 readiness를 준비되지 않은 상태로 표시한다")
    void reportsOutOfServiceBeforeFirstProbe() {
        EvmProofHealthIndicator indicator = new EvmProofHealthIndicator(
                () -> { },
                CLOCK,
                NOOP_TELEMETRY
        );

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails())
                .containsEntry("provider", "evm")
                .containsEntry("errorCode", "NOT_CHECKED")
                .containsEntry("checkedAt", CHECKED_AT);
    }

    @Test
    @DisplayName("일시 RPC 장애는 DOWN으로 표시하고 다음 정상 probe에서 UP으로 복구한다")
    void recoversAfterRetryableRpcFailure() {
        AtomicReference<ProofProviderException> failure = new AtomicReference<>(
                new ProofProviderException(
                        ProofFailureType.RETRYABLE,
                        "RPC_UNAVAILABLE"
                )
        );
        EvmProofHealthIndicator indicator = indicator(failure);

        indicator.refresh();
        var unavailable = indicator.health();
        failure.set(null);
        indicator.refresh();
        var recovered = indicator.health();

        assertThat(unavailable.getStatus()).isEqualTo(Status.DOWN);
        assertThat(unavailable.getDetails())
                .containsEntry("failureType", "RETRYABLE")
                .containsEntry("errorCode", "RPC_UNAVAILABLE");
        assertThat(recovered.getStatus()).isEqualTo(Status.UP);
        assertThat(recovered.getDetails())
                .containsEntry("provider", "evm")
                .doesNotContainKeys("failureType", "errorCode");
    }

    @Test
    @DisplayName("RPC 장애와 회복 probe의 결과와 장애 지속 시간을 기록한다")
    void recordsProbeAndRecoveryTelemetry() {
        AtomicReference<ProofProviderException> failure = new AtomicReference<>(
                new ProofProviderException(
                        ProofFailureType.RETRYABLE,
                        "RPC_UNAVAILABLE"
                )
        );
        MutableClock clock = new MutableClock(CHECKED_AT);
        RecordingProofProviderTelemetry telemetry =
                new RecordingProofProviderTelemetry();
        EvmProofHealthIndicator indicator = new EvmProofHealthIndicator(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
        }, clock, telemetry);

        indicator.refresh();
        clock.advance(Duration.ofSeconds(35));
        failure.set(null);
        indicator.refresh();

        assertThat(telemetry.probeStatuses).containsExactly(
                ProofProviderTelemetry.ProbeStatus.TEMPORARILY_UNAVAILABLE,
                ProofProviderTelemetry.ProbeStatus.AVAILABLE
        );
        assertThat(telemetry.outageDurations)
                .containsExactly(Duration.ofSeconds(35));
    }

    @Test
    @DisplayName("영구 EVM 설정 오류는 재시도 장애와 다른 OUT_OF_SERVICE로 표시한다")
    void reportsPermanentMisconfigurationAsOutOfService() {
        AtomicReference<ProofProviderException> failure = new AtomicReference<>(
                new ProofProviderException(
                        ProofFailureType.PERMANENT,
                        "CHAIN_ID_MISMATCH"
                )
        );
        EvmProofHealthIndicator indicator = indicator(failure);

        indicator.refresh();
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails())
                .containsEntry("failureType", "PERMANENT")
                .containsEntry("errorCode", "CHAIN_ID_MISMATCH");
    }

    private EvmProofHealthIndicator indicator(
            AtomicReference<ProofProviderException> failure
    ) {
        return new EvmProofHealthIndicator(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
        }, CLOCK, NOOP_TELEMETRY);
    }

    private static final class RecordingProofProviderTelemetry
            implements ProofProviderTelemetry {
        private final List<ProbeStatus> probeStatuses = new ArrayList<>();
        private final List<Duration> outageDurations = new ArrayList<>();

        @Override
        public void recordProbe(ProbeStatus status, Duration duration) {
            probeStatuses.add(status);
        }

        @Override
        public void recordRecovery(Duration outageDuration) {
            outageDurations.add(outageDuration);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
