package com.naesan.evidence.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;

@JdbcTest
@Import(TestcontainersConfiguration.class)
class EvidenceFileSchemaTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("3203739b-8c25-42c2-a2d4-2c9222a960c1");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("0a72a817-5a5a-47d8-8bfd-087e1da9c2f2");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final String SHA256 = "a".repeat(64);
    private static final String INSERT_FILE = """
            INSERT INTO evidence_files (
                id,
                evidence_id,
                object_key,
                sha256,
                media_type,
                size_bytes,
                state,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, 'TEMPORARY', ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceFileSchemaTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareEvidence() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'file-owner@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
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
                ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("임시 Evidence 파일 metadata를 저장한다")
    void storesTemporaryFile() {
        insertFile(EVIDENCE_ID, "temporary/one", SHA256, "image/jpeg", 100);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_files",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("하나의 Evidence에 파일을 두 개 연결하지 않는다")
    void rejectsSecondFileForSameEvidence() {
        insertFile(EVIDENCE_ID, "temporary/one", SHA256, "image/jpeg", 100);

        assertThatThrownBy(() -> insertFile(
                EVIDENCE_ID,
                "temporary/two",
                "b".repeat(64),
                "image/png",
                200
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일한 SHA-256을 가진 서로 다른 파일을 허용한다")
    void allowsDuplicateDigest() {
        UUID secondEvidenceId = insertSecondEvidence();
        insertFile(EVIDENCE_ID, "temporary/one", SHA256, "image/jpeg", 100);

        insertFile(secondEvidenceId, "temporary/two", SHA256, "image/jpeg", 100);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_files WHERE sha256 = ?",
                Integer.class,
                SHA256
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("허용하지 않는 MIME 형식을 거절한다")
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(() -> insertFile(
                EVIDENCE_ID,
                "temporary/one",
                SHA256,
                "image/gif",
                100
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertSecondEvidence() {
        UUID evidenceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'DRAFT', ?, ?)
                """,
                evidenceId,
                ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
        return evidenceId;
    }

    private void insertFile(
            UUID evidenceId,
            String objectKey,
            String sha256,
            String mediaType,
            long size
    ) {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                INSERT_FILE,
                UUID.randomUUID(),
                evidenceId,
                objectKey,
                sha256,
                mediaType,
                size,
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
    }
}
