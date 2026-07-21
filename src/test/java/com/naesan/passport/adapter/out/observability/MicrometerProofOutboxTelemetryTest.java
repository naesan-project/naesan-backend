package com.naesan.passport.adapter.out.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.domain.OutboxEventStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerProofOutboxTelemetryTest {

    @Test
    @DisplayName("고정 상태 tag로 처리 결과와 시도 횟수와 지연을 기록한다")
    void recordsBoundedWorkerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerProofOutboxTelemetry telemetry =
                new MicrometerProofOutboxTelemetry(registry);

        telemetry.recordProcessed(
                OutboxEventStatus.RETRY_WAIT,
                3,
                Duration.ofMillis(25)
        );
        telemetry.recordFinalizeRejected();
        telemetry.updateStatusCount(OutboxEventStatus.RETRY_WAIT, 7);

        assertThat(registry.get("naesan.proof.outbox.processed")
                .tag("status", "retry_wait")
                .counter()
                .count()).isOne();
        assertThat(registry.get("naesan.proof.outbox.processing")
                .tag("status", "retry_wait")
                .timer()
                .count()).isOne();
        assertThat(registry.get("naesan.proof.outbox.attempts")
                .tag("status", "retry_wait")
                .summary()
                .totalAmount()).isEqualTo(3);
        assertThat(registry.get("naesan.proof.outbox.events")
                .tag("status", "retry_wait")
                .gauge()
                .value()).isEqualTo(7);
        assertThat(registry.get("naesan.proof.outbox.finalize.rejected")
                .counter()
                .count()).isOne();
    }
}
