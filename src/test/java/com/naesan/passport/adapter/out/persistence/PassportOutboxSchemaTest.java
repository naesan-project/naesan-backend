package com.naesan.passport.adapter.out.persistence;

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
class PassportOutboxSchemaTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("3b3ca582-aac5-4701-ad1c-28a160190909");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("61490a60-80ac-46a6-a337-6bdf2e088f91");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("1738687e-f6a9-4057-97a4-d9c277553762");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PassportOutboxSchemaTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareSnapshot() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'passport-owner@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at, confirmed_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'CONFIRMED', ?, ?, ?)
                """,
                EVIDENCE_ID,
                ACCOUNT_ID,
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
    }

    @Test
    @DisplayName("Passport와 초기 소유 이력, proof, outbox를 연결해 저장한다")
    void storesPassportOutboxGraph() {
        UUID passportId = insertPassport(UUID.randomUUID());
        insertOwnershipHistory(passportId);
        UUID proofAnchorId = insertProofAnchor(passportId, UUID.randomUUID());
        insertOutboxEvent(passportId, proofAnchorId, "proof-anchor:" + proofAnchorId);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                passportId
        );
        assertThat(eventCount).isOne();
    }

    @Test
    @DisplayName("같은 snapshot에는 Passport를 하나만 저장한다")
    void rejectsSecondPassportForSameSnapshot() {
        insertPassport(UUID.randomUUID());

        assertThatThrownBy(() -> insertPassport(UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 Passport에는 proof anchor를 하나만 저장한다")
    void rejectsSecondProofAnchorForSamePassport() {
        UUID passportId = insertPassport(UUID.randomUUID());
        insertProofAnchor(passportId, UUID.randomUUID());

        assertThatThrownBy(() -> insertProofAnchor(passportId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Outbox dispatch key는 중복 저장할 수 없다")
    void rejectsDuplicateDispatchKey() {
        UUID passportId = insertPassport(UUID.randomUUID());
        UUID proofAnchorId = insertProofAnchor(passportId, UUID.randomUUID());
        insertOutboxEvent(passportId, proofAnchorId, "proof-anchor:fixed");

        assertThatThrownBy(() -> insertOutboxEvent(
                passportId,
                proofAnchorId,
                "proof-anchor:fixed"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("이전 소유 이력에는 서로 다른 이전·새 보유자가 필요하다")
    void rejectsInvalidOwnershipTransfer() {
        UUID passportId = insertPassport(UUID.randomUUID());

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO ownership_history (
                    id, passport_id, previous_holder_account_id,
                    new_holder_account_id, reason, changed_at
                )
                VALUES (?, ?, ?, ?, 'TRANSFERRED', ?)
                """,
                UUID.randomUUID(),
                passportId,
                ACCOUNT_ID,
                ACCOUNT_ID,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertPassport(UUID passportId) {
        jdbcTemplate.update(
                """
                INSERT INTO passports (
                    id, snapshot_id, current_holder_account_id,
                    status, version, created_at
                )
                VALUES (?, ?, ?, 'ACTIVE', 0, ?)
                """,
                passportId,
                SNAPSHOT_ID,
                ACCOUNT_ID,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        return passportId;
    }

    private void insertOwnershipHistory(UUID passportId) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_history (
                    id, passport_id, new_holder_account_id, reason, changed_at
                )
                VALUES (?, ?, ?, 'ISSUED', ?)
                """,
                UUID.randomUUID(),
                passportId,
                ACCOUNT_ID,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    private UUID insertProofAnchor(UUID passportId, UUID proofAnchorId) {
        jdbcTemplate.update(
                """
                INSERT INTO proof_anchors (
                    id, passport_id, schema_version, anchor_salt,
                    commitment, state, created_at, updated_at
                )
                VALUES (?, ?, 1, ?, ?, 'PREPARED', ?, ?)
                """,
                proofAnchorId,
                passportId,
                new byte[32],
                new byte[32],
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        return proofAnchorId;
    }

    private void insertOutboxEvent(
            UUID passportId,
            UUID proofAnchorId,
            String dispatchKey
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_type, aggregate_id, proof_anchor_id,
                    schema_version, payload, dispatch_key, status,
                    next_attempt_at, created_at, updated_at
                )
                VALUES (
                    ?, 'PROOF_ANCHOR_REQUESTED', ?, ?,
                    1, CAST(? AS JSONB), ?, 'PENDING',
                    ?, ?, ?
                )
                """,
                UUID.randomUUID(),
                passportId,
                proofAnchorId,
                "{\"schemaVersion\":1,\"commitment\":\"" + "a".repeat(64) + "\"}",
                dispatchKey,
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }
}
