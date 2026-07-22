package com.naesan.share.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.share.domain.PublicShare;
import com.naesan.share.domain.PublicShareCapability;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        PublicShareJdbcRepository.class
})
class PublicShareJdbcRepositoryTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("c92c3982-2fbb-48f0-aa87-b989043896ad");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("af197680-bcae-49cb-a47e-d112535556e7");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("a129cb9d-0824-4e2a-8461-71557c080876");
    private static final UUID PASSPORT_ID =
            UUID.fromString("b4762f83-e11c-4650-8fac-8ba7467e3bf2");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-21T00:00:00Z");

    private final PublicShareJdbcRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicShareJdbcRepositoryTest(
            PublicShareJdbcRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'repository-share-owner@example.com', ?, 'ACTIVE', ?)
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
        jdbcTemplate.update(
                """
                INSERT INTO passports (
                    id, snapshot_id, current_holder_account_id,
                    status, version, created_at
                )
                VALUES (?, ?, ?, 'ACTIVE', 0, ?)
                """,
                PASSPORT_ID,
                SNAPSHOT_ID,
                ACCOUNT_ID,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("share를 hash와 Passport로 조회하고 폐기 상태로 갱신한다")
    void storesFindsAndRevokesShare() {
        byte[] tokenHash = new byte[32];
        PublicShare share = PublicShare.issue(
                UUID.randomUUID(),
                PASSPORT_ID,
                tokenHash,
                PublicShareCapability.FILE_MATCH,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        );
        repository.save(share);

        assertThat(repository.findByTokenHash(tokenHash)).contains(share);
        assertThat(repository.findUnrevokedByPassportId(PASSPORT_ID))
                .contains(share);
        assertThat(repository.findByIdAndPassportId(share.id(), PASSPORT_ID))
                .contains(share);

        PublicShare revokedShare = share.revoke(
                CREATED_AT.plus(1, ChronoUnit.HOURS)
        );
        repository.update(revokedShare);

        assertThat(repository.findUnrevokedByPassportId(PASSPORT_ID)).isEmpty();
        assertThat(repository.findByTokenHash(tokenHash))
                .hasValueSatisfying(foundShare -> assertThat(foundShare.revokedAt())
                        .isEqualTo(revokedShare.revokedAt()));
    }

    @Test
    @DisplayName("Passport의 활성 share를 한 번에 폐기한다")
    void revokesAllActiveSharesByPassport() {
        byte[] tokenHash = new byte[32];
        PublicShare share = PublicShare.issue(
                UUID.randomUUID(),
                PASSPORT_ID,
                tokenHash,
                PublicShareCapability.SUMMARY,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        );
        repository.save(share);
        Instant revokedAt = CREATED_AT.plus(1, ChronoUnit.HOURS);

        int revokedShareCount = repository.revokeAllByPassportId(
                PASSPORT_ID,
                revokedAt
        );

        assertThat(revokedShareCount).isOne();
        assertThat(repository.findUnrevokedByPassportId(PASSPORT_ID)).isEmpty();
        assertThat(repository.findByTokenHash(tokenHash))
                .hasValueSatisfying(foundShare -> assertThat(foundShare.revokedAt())
                        .isEqualTo(revokedAt));
    }
}
