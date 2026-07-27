package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.adapter.out.observability.MicrometerProofOutboxTelemetry;
import com.naesan.passport.domain.OutboxEventStatus;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

class PrometheusProofOutboxMetricsTest {

    @Test
    @DisplayName("Prometheus scrape는 고정 status tag의 outbox 상태와 처리 결과를 제공한다")
    void exposesProofOutboxMetricsWithoutDynamicIdentifiers() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT
        );
        MicrometerProofOutboxTelemetry telemetry =
                new MicrometerProofOutboxTelemetry(registry);
        telemetry.updateStatusCount(OutboxEventStatus.DEAD_LETTER, 2);
        telemetry.recordProcessed(
                OutboxEventStatus.SUCCEEDED,
                1,
                Duration.ofMillis(25)
        );

        String scrape = registry.scrape();

        assertThat(scrape)
                .contains(
                        "naesan_proof_outbox_events{status=\"dead_letter\"} 2.0"
                )
                .contains(
                        "naesan_proof_outbox_processed_total{status=\"succeeded\"} 1.0"
                )
                .doesNotContain("account_id")
                .doesNotContain("passport_id")
                .doesNotContain("worker_id");
    }
}
