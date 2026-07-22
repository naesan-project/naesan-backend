package com.naesan.passport.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.domain.AnchorCommitment;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OwnershipChangeReason;
import com.naesan.passport.domain.OwnershipHistory;
import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.ProofAnchor;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        PassportJdbcRepository.class,
        OwnershipHistoryJdbcRepository.class,
        ProofAnchorJdbcRepository.class,
        OutboxEventJdbcRepository.class
})
class PassportPersistenceTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("d20c2a01-4f15-4daf-a252-b936002c6540");
    private static final UUID NEW_HOLDER_ACCOUNT_ID =
            UUID.fromString("c6899041-e95a-45a5-9769-5691ceef1e0b");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("66af044b-daa9-4ae3-9469-6b1fae889848");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("32147eb0-a26e-4b06-9434-c7b6aeea5fd8");
    private static final UUID PASSPORT_ID =
            UUID.fromString("2c8e7421-4514-4b1c-bf75-3971a44596cc");
    private static final UUID PROOF_ANCHOR_ID =
            UUID.fromString("0e6046ac-f0d9-4372-a352-23f29d64e04c");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final PassportJdbcRepository passportRepository;
    private final OwnershipHistoryJdbcRepository ownershipHistoryRepository;
    private final ProofAnchorJdbcRepository proofAnchorRepository;
    private final OutboxEventJdbcRepository outboxEventRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PassportPersistenceTest(
            PassportJdbcRepository passportRepository,
            OwnershipHistoryJdbcRepository ownershipHistoryRepository,
            ProofAnchorJdbcRepository proofAnchorRepository,
            OutboxEventJdbcRepository outboxEventRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.passportRepository = passportRepository;
        this.ownershipHistoryRepository = ownershipHistoryRepository;
        this.proofAnchorRepository = proofAnchorRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareSnapshot() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'passport-persistence@example.com', ?, 'ACTIVE', ?)
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
    @DisplayName("Passport 발급 상태 전체를 저장하고 각 경계에서 복원한다")
    void savesAndRestoresPassportIssuanceState() {
        Passport passport = Passport.issue(
                PASSPORT_ID,
                SNAPSHOT_ID,
                ACCOUNT_ID,
                CREATED_AT
        );
        OwnershipHistory ownershipHistory = OwnershipHistory.recordIssuance(
                UUID.randomUUID(),
                PASSPORT_ID,
                ACCOUNT_ID,
                CREATED_AT
        );
        AnchorCommitment anchorCommitment = new AnchorCommitment(
                1,
                new byte[32],
                HexFormat.of().parseHex("a".repeat(64))
        );
        ProofAnchor proofAnchor = ProofAnchor.prepare(
                PROOF_ANCHOR_ID,
                PASSPORT_ID,
                anchorCommitment,
                CREATED_AT
        );
        OutboxEvent outboxEvent = OutboxEvent.createProofAnchorRequest(
                UUID.randomUUID(),
                PASSPORT_ID,
                PROOF_ANCHOR_ID,
                1,
                "{\"schemaVersion\":1,\"commitment\":\"" + "a".repeat(64) + "\"}",
                "proof-anchor:" + PROOF_ANCHOR_ID,
                CREATED_AT
        );

        passportRepository.save(passport);
        ownershipHistoryRepository.append(ownershipHistory);
        proofAnchorRepository.save(proofAnchor);
        outboxEventRepository.save(outboxEvent);

        assertThat(passportRepository.findBySnapshotId(SNAPSHOT_ID)).contains(passport);
        assertThat(passportRepository.findAllByCurrentHolderAccountId(ACCOUNT_ID))
                .containsExactly(passport);
        assertThat(ownershipHistoryRepository.findAllByPassportId(PASSPORT_ID))
                .singleElement()
                .satisfies(savedHistory -> {
                    assertThat(savedHistory.reason())
                            .isEqualTo(OwnershipChangeReason.ISSUED);
                    assertThat(savedHistory.previousHolderAccountId()).isNull();
                });
        assertThat(proofAnchorRepository.findByPassportId(PASSPORT_ID))
                .get()
                .satisfies(savedProof -> assertThat(savedProof.commitment())
                        .isEqualTo(anchorCommitment.commitment()));
        assertThat(outboxEventRepository.findByProofAnchorId(PROOF_ANCHOR_ID))
                .get()
                .satisfies(savedEvent -> {
                    assertThat(savedEvent.dispatchKey())
                            .isEqualTo(outboxEvent.dispatchKey());
                    assertThat(savedEvent.payload()).contains("\"commitment\"");
                });
    }

    @Test
    @DisplayName("Passport holder와 version을 조건부 갱신하고 이전 이력을 append한다")
    void updatesPassportHolderAndAppendsHistory() {
        insertNewHolderAccount();
        Passport passport = Passport.issue(
                PASSPORT_ID,
                SNAPSHOT_ID,
                ACCOUNT_ID,
                CREATED_AT
        );
        passportRepository.save(passport);
        Passport transferredPassport = passport.transferTo(
                ACCOUNT_ID,
                NEW_HOLDER_ACCOUNT_ID
        );
        OwnershipHistory history = OwnershipHistory.recordTransfer(
                UUID.randomUUID(),
                PASSPORT_ID,
                ACCOUNT_ID,
                NEW_HOLDER_ACCOUNT_ID,
                CREATED_AT.plusSeconds(1)
        );

        assertThat(passportRepository.update(transferredPassport, 0)).isTrue();
        ownershipHistoryRepository.append(history);

        assertThat(passportRepository.findById(PASSPORT_ID))
                .contains(transferredPassport);
        assertThat(passportRepository.update(passport, 0)).isFalse();
        assertThat(ownershipHistoryRepository.findAllByPassportId(PASSPORT_ID))
                .singleElement()
                .satisfies(savedHistory -> {
                    assertThat(savedHistory.previousHolderAccountId())
                            .isEqualTo(ACCOUNT_ID);
                    assertThat(savedHistory.newHolderAccountId())
                            .isEqualTo(NEW_HOLDER_ACCOUNT_ID);
                    assertThat(savedHistory.reason())
                            .isEqualTo(OwnershipChangeReason.TRANSFERRED);
                });
    }

    private void insertNewHolderAccount() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'passport-new-holder@example.com', ?, 'ACTIVE', ?)
                """,
                NEW_HOLDER_ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }
}
