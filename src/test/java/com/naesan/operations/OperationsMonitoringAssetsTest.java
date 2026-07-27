package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationsMonitoringAssetsTest {
    private static final Path DASHBOARD_PATH = Path.of(
            "operations/grafana/naesan-overview.json"
    );
    private static final Path ALERTS_PATH = Path.of(
            "operations/prometheus/naesan-alerts.yml"
    );

    @Test
    @DisplayName("운영 dashboard는 outbox 상태와 처리량과 JVM 상태를 표시한다")
    void coversOperationalSignals() throws IOException {
        String dashboardContent = Files.readString(DASHBOARD_PATH);

        assertThat(dashboardContent)
                .contains("\"title\": \"Naesan Operations\"")
                .contains("\"id\": 1")
                .contains("\"id\": 2")
                .contains("\"id\": 3")
                .contains("\"id\": 4")
                .contains("naesan_proof_outbox_events")
                .contains("naesan_proof_outbox_processed_total")
                .contains("naesan_proof_outbox_finalize_rejected_total")
                .contains("jvm_memory_used_bytes")
                .doesNotContain("account_id")
                .doesNotContain("passport_id")
                .doesNotContain("worker_id");
    }

    @Test
    @DisplayName("운영 alert는 dead letter와 manual review와 finalize 거절을 감지한다")
    void coversActionableOutboxFailures() throws IOException {
        String alerts = Files.readString(ALERTS_PATH);

        assertThat(alerts)
                .contains("NaesanProofOutboxDeadLetter")
                .contains("NaesanProofOutboxManualReview")
                .contains("NaesanProofOutboxFinalizeRejected")
                .contains("severity: critical")
                .contains("severity: warning")
                .doesNotContain("account_id")
                .doesNotContain("passport_id")
                .doesNotContain("worker_id");
    }
}
