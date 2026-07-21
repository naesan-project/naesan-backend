package com.naesan.passport.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.ReprocessOutboxEventCommand;
import com.naesan.passport.application.ReprocessProofOutboxService;
import com.naesan.passport.application.port.out.ProofProviderCapabilities;
import com.naesan.passport.domain.OutboxReprocessAudit;
import com.naesan.passport.support.ControllableProofAnchorAdapter;
import com.naesan.passport.support.ControllableProofAnchorAdapter.ProofOutcome;

@SpringBootTest
@Import({
        TestcontainersConfiguration.class,
        ProcessProofOutboxIntegrationTest.RecordingProofConfiguration.class
})
class ReprocessProofOutboxIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("a8d34865-13f4-441e-a365-fb158edb8796");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("c8be75cb-3801-41fd-b0d6-cf4157a912a8");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("a3bded90-f71b-486e-8899-dcfb59747822");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final IssuePassportService issuePassportService;
    private final ProcessProofOutboxService processProofOutboxService;
    private final ReprocessProofOutboxService reprocessProofOutboxService;
    private final ControllableProofAnchorAdapter proofAnchorPort;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReprocessProofOutboxIntegrationTest(
            IssuePassportService issuePassportService,
            ProcessProofOutboxService processProofOutboxService,
            ReprocessProofOutboxService reprocessProofOutboxService,
            ControllableProofAnchorAdapter proofAnchorPort,
            JdbcTemplate jdbcTemplate
    ) {
        this.issuePassportService = issuePassportService;
        this.processProofOutboxService = processProofOutboxService;
        this.reprocessProofOutboxService = reprocessProofOutboxService;
        this.proofAnchorPort = proofAnchorPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareIssuedPassport() {
        proofAnchorPort.reset();
        jdbcTemplate.update("DELETE FROM outbox_reprocess_audit");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertConfirmedEvidence();
        issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID);
    }

    private void insertConfirmedEvidence() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'proof-reprocess@example.com', ?, 'ACTIVE', ?)
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
                "b".repeat(64),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("dead letter 재처리는 명령을 보존하고 감사 기록과 함께 새 제출을 예약한다")
    void reprocessesDeadLetterWithAudit() {
        proofAnchorPort.setOutcome(ProofOutcome.PERMANENT_FAILURE);
        processProofOutboxService.processNext("worker-1");
        UUID eventId = eventId();
        String previousPayload = outboxPayload();

        OutboxReprocessAudit audit = reprocessProofOutboxService.reprocess(
                new ReprocessOutboxEventCommand(
                        eventId,
                        "operator-1",
                        "provider 설정 복구"
                )
        );

        assertThat(outboxState()).isEqualTo(
                new OutboxState("PENDING", 0, 1, null, null)
        );
        assertThat(outboxPayload()).isEqualTo(previousPayload);
        assertThat(audit.reprocessNumber()).isOne();
        assertThat(auditCount()).isOne();
    }

    @Test
    @DisplayName("manual review 재처리는 lookup-first 대사를 재개해 기존 성공을 확정한다")
    void resumesLookupFirstFromManualReview() {
        proofAnchorPort.setOutcome(ProofOutcome.SUCCESS_THEN_RESPONSE_LOSS);
        processProofOutboxService.processNext("worker-1");
        proofAnchorPort.setCapabilities(new ProofProviderCapabilities(false, true));
        processProofOutboxService.processNext("worker-2");
        UUID eventId = eventId();

        reprocessProofOutboxService.reprocess(new ReprocessOutboxEventCommand(
                eventId,
                "operator-1",
                "lookup provider 복구"
        ));

        assertThat(outboxStatus()).isEqualTo("RECONCILE_PENDING");
        assertThat(proofState()).isEqualTo("RECONCILE_PENDING");

        proofAnchorPort.setCapabilities(new ProofProviderCapabilities(true, true));
        processProofOutboxService.processNext("worker-3");

        assertThat(outboxStatus()).isEqualTo("SUCCEEDED");
        assertThat(proofState()).isEqualTo("CONFIRMED");
        assertThat(proofAnchorPort.submitCount()).isOne();
        assertThat(proofAnchorPort.lookupCount()).isOne();
    }

    private UUID eventId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_events",
                UUID.class
        );
    }

    private String outboxPayload() {
        return jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events",
                String.class
        );
    }

    private OutboxState outboxState() {
        return jdbcTemplate.query(
                """
                SELECT
                    status,
                    attempt_count,
                    reprocess_count,
                    error_category,
                    error_code
                FROM outbox_events
                """,
                (resultSet, rowNumber) -> new OutboxState(
                        resultSet.getString("status"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getInt("reprocess_count"),
                        resultSet.getString("error_category"),
                        resultSet.getString("error_code")
                )
        ).getFirst();
    }

    private String outboxStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events",
                String.class
        );
    }

    private String proofState() {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM proof_anchors",
                String.class
        );
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_reprocess_audit",
                Integer.class
        );
    }

    private record OutboxState(
            String status,
            int attemptCount,
            int reprocessCount,
            String errorCategory,
            String errorCode
    ) {
    }
}
