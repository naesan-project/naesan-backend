package com.naesan.share.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;

@JdbcTest
@Import(TestcontainersConfiguration.class)
class PublicShareSchemaTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("530538c7-dc60-4991-8c28-dd797cfddcc5");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("1870936b-a705-47c2-b84c-fe1dc45aa100");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("ea8906fd-b0fb-4cc8-934b-ee98e65d748d");
    private static final UUID PASSPORT_ID =
            UUID.fromString("b6741776-f776-4766-a136-0a2f93676438");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-21T00:00:00Z");

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicShareSchemaTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'share-owner@example.com', ?, 'ACTIVE', ?)
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
    @DisplayName("Passport에는 revoke되지 않은 share를 하나만 저장한다")
    void allowsOneUnrevokedSharePerPassport() {
        insertShare(new byte[32]);

        assertThatThrownBy(() -> insertShare(hashStartingWith(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("이전 share를 revoke한 뒤 새 hash로 rotation한다")
    void rotatesShareAfterRevocation() {
        UUID previousShareId = insertShare(new byte[32]);
        jdbcTemplate.update(
                "UPDATE public_shares SET revoked_at = ? WHERE id = ?",
                CREATED_AT.plus(1, ChronoUnit.HOURS).atOffset(ZoneOffset.UTC),
                previousShareId
        );

        insertShare(hashStartingWith(1));

        Integer shareCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_shares WHERE passport_id = ?",
                Integer.class,
                PASSPORT_ID
        );
        assertThat(shareCount).isEqualTo(2);
    }

    @Test
    @DisplayName("public share table에는 raw token column이 없다")
    void storesOnlyTokenHash() {
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'public_shares'
                ORDER BY ordinal_position
                """,
                String.class
        )).containsExactly(
                "id",
                "passport_id",
                "token_hash",
                "capability",
                "expires_at",
                "revoked_at",
                "created_at"
        );
    }

    private UUID insertShare(byte[] tokenHash) {
        UUID shareId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO public_shares (
                    id, passport_id, token_hash, capability,
                    expires_at, created_at
                )
                VALUES (?, ?, ?, 'SUMMARY', ?, ?)
                """,
                shareId,
                PASSPORT_ID,
                tokenHash,
                CREATED_AT.plus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        return shareId;
    }

    private byte[] hashStartingWith(int firstByte) {
        byte[] tokenHash = new byte[32];
        tokenHash[0] = (byte) firstByte;
        return tokenHash;
    }
}
