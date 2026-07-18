package com.naesan.evidence.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;

@JdbcTest
@Import(TestcontainersConfiguration.class)
class EvidenceSnapshotSchemaTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("44658cc4-504d-40b2-bb47-9379c7645327");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("30d98a4b-353b-42bf-95ba-6c3a80acf39a");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final String INSERT_SNAPSHOT = """
            INSERT INTO evidence_snapshots (
                id, evidence_id, schema_version, canonical_payload,
                snapshot_digest, created_at
            )
            VALUES (?, ?, 1, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceSnapshotSchemaTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareEvidence() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'snapshot-owner@example.com', ?, 'ACTIVE', ?)
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
    @DisplayName("Evidence snapshot canonical bytes를 저장한다")
    void storesSnapshot() {
        insertSnapshot(UUID.randomUUID(), "a".repeat(64));

        byte[] payload = jdbcTemplate.queryForObject(
                "SELECT canonical_payload FROM evidence_snapshots",
                byte[].class
        );
        assertThat(payload).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Evidence당 snapshot을 하나만 저장한다")
    void rejectsSecondSnapshotForSameEvidence() {
        insertSnapshot(UUID.randomUUID(), "a".repeat(64));

        assertThatThrownBy(() -> insertSnapshot(UUID.randomUUID(), "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertSnapshot(UUID snapshotId, String digest) {
        jdbcTemplate.update(
                INSERT_SNAPSHOT,
                snapshotId,
                EVIDENCE_ID,
                "{}".getBytes(StandardCharsets.UTF_8),
                digest,
                Instant.parse("2026-07-18T00:00:00Z").atOffset(ZoneOffset.UTC)
        );
    }
}
