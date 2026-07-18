package com.naesan.evidence.adapter.out.persistence;

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
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.evidence.domain.EvidenceSnapshot;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        EvidenceSnapshotJdbcRepository.class
})
class EvidenceSnapshotJdbcRepositoryTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("cf88412b-2083-4910-b13b-1ddb9bfc44cf");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("f45c76c5-a096-4ad1-a083-fe6b5964882c");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("b9798322-cd27-45ea-88d0-2ac9d2523bd0");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final EvidenceSnapshotJdbcRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceSnapshotJdbcRepositoryTest(
            EvidenceSnapshotJdbcRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareEvidence() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'snapshot-repository@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
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
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Evidence snapshot을 정확한 canonical bytes로 저장하고 복원한다")
    void savesAndFindsSnapshot() {
        EvidenceSnapshot snapshot = new EvidenceSnapshot(
                SNAPSHOT_ID,
                EVIDENCE_ID,
                1,
                "{}".getBytes(StandardCharsets.UTF_8),
                "a".repeat(64),
                CREATED_AT
        );

        repository.save(snapshot);

        assertThat(repository.findByEvidenceId(EVIDENCE_ID))
                .contains(snapshot)
                .get()
                .satisfies(savedSnapshot -> {
                    assertThat(savedSnapshot.canonicalPayload())
                            .isEqualTo(snapshot.canonicalPayload());
                    assertThat(savedSnapshot.snapshotDigest())
                            .isEqualTo(snapshot.snapshotDigest());
                });
    }
}
