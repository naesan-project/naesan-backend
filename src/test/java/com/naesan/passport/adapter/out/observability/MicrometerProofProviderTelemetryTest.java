package com.naesan.passport.adapter.out.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.application.port.out.ProofProviderTelemetry.ProbeStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerProofProviderTelemetryTest {

    @Test
    @DisplayName("고정 probe 결과로 가용 상태와 지연과 회복 시간을 기록한다")
    void recordsBoundedProviderMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerProofProviderTelemetry telemetry =
                new MicrometerProofProviderTelemetry(registry);

        telemetry.recordProbe(
                ProbeStatus.TEMPORARILY_UNAVAILABLE,
                Duration.ofMillis(125)
        );
        telemetry.recordProbe(ProbeStatus.AVAILABLE, Duration.ofMillis(25));
        telemetry.recordRecovery(Duration.ofSeconds(35));

        assertThat(registry.get("naesan.proof.provider.probes")
                .tag("result", "temporarily_unavailable")
                .counter()
                .count()).isOne();
        assertThat(registry.get("naesan.proof.provider.probe")
                .tag("result", "available")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(25);
        assertThat(registry.get("naesan.proof.provider.available")
                .gauge()
                .value()).isOne();
        assertThat(registry.get("naesan.proof.provider.outage")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(35);
    }
}
