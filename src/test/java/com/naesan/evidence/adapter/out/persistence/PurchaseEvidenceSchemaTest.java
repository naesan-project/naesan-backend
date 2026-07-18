package com.naesan.evidence.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
class PurchaseEvidenceSchemaTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("89cb9d74-ce63-4e0a-89a5-1a5db6571474");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final String INSERT_EVIDENCE = """
            INSERT INTO purchase_evidence (
                id,
                owner_account_id,
                merchant_name,
                product_name,
                serial_number,
                purchased_at,
                amount,
                currency,
                state,
                created_at,
                updated_at
            )
            VALUES (?, ?, '생각상점', '생각등대', NULL, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PurchaseEvidenceSchemaTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void insertAccount() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'owner@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
                BCRYPT_HASH,
                Timestamp.from(Instant.parse("2026-07-18T00:00:00Z"))
        );
    }

    @Test
    @DisplayName("유효한 구매 증빙 draft를 저장한다")
    void storesValidDraft() {
        insertEvidence("1000.00", "KRW", "DRAFT");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_evidence",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("음수 구매 금액을 거절한다")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> insertEvidence("-0.01", "KRW", "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("대문자 ASCII 세 글자가 아닌 통화를 거절한다")
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> insertEvidence("1000.00", "krw", "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정의되지 않은 Evidence 상태를 거절한다")
    void rejectsUnknownState() {
        assertThatThrownBy(() -> insertEvidence("1000.00", "KRW", "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 계정을 owner로 저장하지 않는다")
    void rejectsUnknownOwner() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                INSERT_EVIDENCE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Date.valueOf(LocalDate.parse("2026-07-01")),
                new BigDecimal("1000.00"),
                "KRW",
                "DRAFT",
                Timestamp.from(Instant.parse("2026-07-18T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-07-18T00:00:00Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertEvidence(String amount, String currency, String state) {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                INSERT_EVIDENCE,
                UUID.randomUUID(),
                ACCOUNT_ID,
                Date.valueOf(LocalDate.parse("2026-07-01")),
                new BigDecimal(amount),
                currency,
                state,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }
}
