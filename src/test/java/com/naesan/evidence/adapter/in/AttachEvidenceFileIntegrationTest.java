package com.naesan.evidence.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.naesan.evidence.application.EvidenceErrorCode;
import com.naesan.evidence.application.EvidenceException;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.PurchaseEvidenceState;

@SpringBootTest(properties = "naesan.storage.local.root=build/test-storage/attach")
@Import(TestcontainersConfiguration.class)
class AttachEvidenceFileIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("27a17452-c3dc-465e-a52d-3cd69ddad06e");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("5d2e639a-189c-43ee-945c-e106ce267a88");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final byte[] PDF_CONTENT =
            "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8);

    private final AttachEvidenceFileService service;
    private final FileStorage fileStorage;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AttachEvidenceFileIntegrationTest(
            AttachEvidenceFileService service,
            FileStorage fileStorage,
            JdbcTemplate jdbcTemplate
    ) {
        this.service = service;
        this.fileStorage = fileStorage;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareDraft() throws IOException {
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        clearStorage();

        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'attach-owner@example.com', ?, 'ACTIVE', ?)
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
    @DisplayName("검증한 파일 metadata와 Evidence 상태를 하나의 transaction으로 저장한다")
    void attachesFileToDraft() throws IOException {
        EvidenceFile evidenceFile = service.attach(
                OWNER_ACCOUNT_ID,
                EVIDENCE_ID,
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        );

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM purchase_evidence WHERE id = ?",
                String.class,
                EVIDENCE_ID
        );
        assertThat(state).isEqualTo(PurchaseEvidenceState.FILE_ATTACHED.name());
        try (InputStream storedContent = fileStorage.open(evidenceFile.objectKey())) {
            assertThat(storedContent.readAllBytes()).isEqualTo(PDF_CONTENT);
        }
    }

    @Test
    @DisplayName("다른 계정은 파일을 저장하지 않고 Evidence 존재도 확인할 수 없다")
    void hidesEvidenceFromOtherAccount() throws IOException {
        assertThatThrownBy(() -> service.attach(
                UUID.randomUUID(),
                EVIDENCE_ID,
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        ))
                .isInstanceOf(EvidenceException.class)
                .extracting(exception -> ((EvidenceException) exception).code())
                .isEqualTo(EvidenceErrorCode.EVIDENCE_NOT_FOUND);
        assertThat(storedFileCount()).isZero();
    }

    private void clearStorage() throws IOException {
        Path root = Path.of("build/test-storage/attach");
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(root))
                    .forEach(path -> path.toFile().delete());
        }
    }

    private long storedFileCount() throws IOException {
        Path root = Path.of("build/test-storage/attach");
        if (!Files.exists(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}
