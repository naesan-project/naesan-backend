package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.adapter.out.observability.MicrometerProofProviderTelemetry;
import com.naesan.passport.application.port.out.ProofProviderTelemetry.ProbeStatus;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

class PrometheusProofProviderMetricsTest {

    @Test
    @DisplayName("Prometheus scrape는 고정 결과 tag의 EVM 가용성과 회복 지표를 제공한다")
    void exposesProofProviderMetricsWithoutFailureIdentifiers() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT
        );
        MicrometerProofProviderTelemetry telemetry =
                new MicrometerProofProviderTelemetry(registry);
        telemetry.recordProbe(
                ProbeStatus.TEMPORARILY_UNAVAILABLE,
                Duration.ofMillis(125)
        );
        telemetry.recordProbe(ProbeStatus.AVAILABLE, Duration.ofMillis(25));
        telemetry.recordRecovery(Duration.ofSeconds(35));

        String scrape = registry.scrape();

        assertThat(scrape)
                .contains("naesan_proof_provider_available 1.0")
                .contains(
                        "naesan_proof_provider_probes_total{result=\"temporarily_unavailable\"} 1.0"
                )
                .contains(
                        "naesan_proof_provider_probes_total{result=\"available\"} 1.0"
                )
                .contains("naesan_proof_provider_outage_seconds_count 1")
                .doesNotContain("error_code")
                .doesNotContain("rpc_url")
                .doesNotContain("wallet")
                .doesNotContain("transaction");
    }
}
