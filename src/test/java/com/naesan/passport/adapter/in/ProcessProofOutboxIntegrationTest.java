package com.naesan.passport.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.support.ControllableProofAnchorAdapter;
import com.naesan.passport.support.ControllableProofAnchorAdapter.ProofOutcome;

@SpringBootTest
@Import({
        TestcontainersConfiguration.class,
        ProcessProofOutboxIntegrationTest.RecordingProofConfiguration.class
})
class ProcessProofOutboxIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("d848616d-32d3-49b3-9d99-a65ecab0835b");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("1e6a67f6-a6fa-4661-8c91-5c38f61d6ef3");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("6ed04a73-e85d-46ef-8c4a-b8ece309ef28");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final IssuePassportService issuePassportService;
    private final ProcessProofOutboxService processProofOutboxService;
    private final ControllableProofAnchorAdapter proofAnchorPort;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ProcessProofOutboxIntegrationTest(
            IssuePassportService issuePassportService,
            ProcessProofOutboxService processProofOutboxService,
            ControllableProofAnchorAdapter proofAnchorPort,
            JdbcTemplate jdbcTemplate
    ) {
        this.issuePassportService = issuePassportService;
        this.processProofOutboxService = processProofOutboxService;
        this.proofAnchorPort = proofAnchorPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareIssuedPassport() {
        proofAnchorPort.reset();
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'proof-worker@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, version, created_at, updated_at, confirmed_at
                )
                VALUES (
                    ?, ?, '생각상점', '생각등대', ?,
                    ?, 'KRW', 'CONFIRMED', 1, ?, ?, ?
                )
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO evidence_snapshots (
                    id, evidence_id, schema_version, canonical_payload,
                    snapshot_digest, created_at
                )
                VALUES (?, ?, 1, ?, ?, ?)
                """,
                SNAPSHOT_ID,
                EVIDENCE_ID,
                "{}".getBytes(StandardCharsets.UTF_8),
                "a".repeat(64),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID);
    }

    @Test
    @DisplayName("외부 호출을 DB transaction 밖에서 수행하고 proof와 event를 함께 완료한다")
    void processesProofOutsideDatabaseTransaction() {
        boolean processed = processProofOutboxService.processNext("worker-1");

        assertThat(processed).isTrue();
        assertThat(proofAnchorPort.transactionActiveDuringSubmit()).isFalse();
        assertThat(passportStatus()).isEqualTo("ACTIVE");
        assertThat(proofState()).isEqualTo("CONFIRMED");
        assertThat(outboxStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("일시적 외부 호출 실패는 Passport를 유지하고 retry를 예약한다")
    void keepsPassportActiveWhenExternalCallFails() {
        proofAnchorPort.setOutcome(ProofOutcome.RETRYABLE_FAILURE);

        boolean processed = processProofOutboxService.processNext("worker-1");

        assertThat(processed).isTrue();
        assertThat(passportStatus()).isEqualTo("ACTIVE");
        assertThat(proofState()).isEqualTo("PREPARED");
        assertThat(outboxStatus()).isEqualTo("RETRY_WAIT");
        assertThat(processProofOutboxService.processNext("worker-2")).isFalse();
    }

    @Test
    @DisplayName("영구 외부 호출 실패는 즉시 dead letter로 종료한다")
    void movesPermanentFailureToDeadLetter() {
        proofAnchorPort.setOutcome(ProofOutcome.PERMANENT_FAILURE);

        boolean processed = processProofOutboxService.processNext("worker-1");

        assertThat(processed).isTrue();
        assertThat(outboxStatus()).isEqualTo("DEAD_LETTER");
        assertThat(outboxError())
                .containsExactly("PERMANENT", "INVALID_COMMAND");
    }

    @Test
    @DisplayName("일시적 실패가 최대 횟수에 도달하면 dead letter로 종료한다")
    void movesExhaustedRetryToDeadLetter() {
        proofAnchorPort.setOutcome(ProofOutcome.RETRYABLE_FAILURE);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(processProofOutboxService.processNext("worker-" + attempt))
                    .isTrue();
            if (attempt < 5) {
                makeRetryDue();
            }
        }

        assertThat(outboxStatus()).isEqualTo("DEAD_LETTER");
        assertThat(outboxAttemptCount()).isEqualTo(5);
        assertThat(outboxError())
                .containsExactly("RETRYABLE", "PROVIDER_UNAVAILABLE");
    }

    @Test
    @DisplayName("Baseline은 외부 성공 후 응답 유실도 CLAIMED에 고착시킨다")
    void leavesAmbiguousSuccessClaimed() {
        proofAnchorPort.setOutcome(ProofOutcome.SUCCESS_THEN_RESPONSE_LOSS);

        assertThatThrownBy(() ->
                processProofOutboxService.processNext("worker-1")
        ).isInstanceOf(ProofProviderException.class);

        assertThat(outboxStatus()).isEqualTo("CLAIMED");
        assertThat(proofAnchorPort.submitCount()).isOne();
        assertThat(processProofOutboxService.processNext("worker-2")).isFalse();
    }

    private String passportStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM passports",
                String.class
        );
    }

    private String proofState() {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM proof_anchors",
                String.class
        );
    }

    private String outboxStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events",
                String.class
        );
    }

    private int outboxAttemptCount() {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_events",
                Integer.class
        );
    }

    private List<String> outboxError() {
        return jdbcTemplate.query(
                "SELECT error_category, error_code FROM outbox_events",
                (resultSet, rowNumber) -> List.of(
                        resultSet.getString("error_category"),
                        resultSet.getString("error_code")
                )
        ).getFirst();
    }

    private void makeRetryDue() {
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET next_attempt_at = clock_timestamp() - INTERVAL '1 second'
                """
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingProofConfiguration {

        @Bean
        @Primary
        ControllableProofAnchorAdapter controllableProofAnchorAdapter() {
            return new ControllableProofAnchorAdapter(Clock.systemUTC());
        }
    }
}
