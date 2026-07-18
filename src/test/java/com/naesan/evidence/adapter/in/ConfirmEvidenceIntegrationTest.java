package com.naesan.evidence.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
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
import com.naesan.evidence.application.AttachEvidenceFileService;
import com.naesan.evidence.application.ConfirmEvidenceService;
import com.naesan.evidence.application.EvidenceException;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidenceState;

@SpringBootTest(properties = "naesan.storage.local.root=build/test-storage/confirm")
@Import(TestcontainersConfiguration.class)
class ConfirmEvidenceIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("b11ed6ce-e270-4c43-89ed-2abe69df5869");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("923cc936-da19-4096-9f32-af94003dc2cb");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final byte[] PDF_CONTENT =
            "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8);

    private final AttachEvidenceFileService attachService;
    private final ConfirmEvidenceService confirmService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ConfirmEvidenceIntegrationTest(
            AttachEvidenceFileService attachService,
            ConfirmEvidenceService confirmService,
            JdbcTemplate jdbcTemplate
    ) {
        this.attachService = attachService;
        this.confirmService = confirmService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareDraft() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'confirm-owner@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                now.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'DRAFT', ?, ?)
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("파일 승격과 snapshot 생성과 Evidence 확정을 완료한다")
    void confirmsEvidence() {
        attachFile();

        EvidenceSnapshot snapshot = confirmService.confirm(
                OWNER_ACCOUNT_ID,
                EVIDENCE_ID
        );

        assertThat(evidenceState()).isEqualTo(PurchaseEvidenceState.CONFIRMED.name());
        assertThat(fileState()).isEqualTo(EvidenceFileState.PROMOTED.name());
        assertThat(snapshot.snapshotDigest()).hasSize(64);
        assertThat(snapshotCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("확정 재요청은 기존 snapshot을 반환한다")
    void returnsExistingSnapshotForRepeatedConfirmation() {
        attachFile();
        EvidenceSnapshot first = confirmService.confirm(OWNER_ACCOUNT_ID, EVIDENCE_ID);

        EvidenceSnapshot second = confirmService.confirm(OWNER_ACCOUNT_ID, EVIDENCE_ID);

        assertThat(second).isEqualTo(first);
        assertThat(snapshotCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("파일이 없는 draft는 확정하지 않는다")
    void rejectsDraftWithoutFile() {
        assertThatThrownBy(() -> confirmService.confirm(
                OWNER_ACCOUNT_ID,
                EVIDENCE_ID
        )).isInstanceOf(EvidenceException.class);
        assertThat(snapshotCount()).isZero();
    }

    private void attachFile() {
        attachService.attach(
                OWNER_ACCOUNT_ID,
                EVIDENCE_ID,
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        );
    }

    private String evidenceState() {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM purchase_evidence WHERE id = ?",
                String.class,
                EVIDENCE_ID
        );
    }

    private String fileState() {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM evidence_files WHERE evidence_id = ?",
                String.class,
                EVIDENCE_ID
        );
    }

    private int snapshotCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_snapshots WHERE evidence_id = ?",
                Integer.class,
                EVIDENCE_ID
        );
    }
}
