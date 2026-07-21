package com.naesan.share.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.share.domain.PublicShareCapability;

@SpringBootTest
@Import({
        TestcontainersConfiguration.class,
        ManagePublicShareServiceIntegrationTest.FixedClockConfiguration.class
})
class ManagePublicShareServiceIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("d68f4678-4c76-4e95-bd86-2fff5c7aa416");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("a042f1e6-4a22-4ac4-8968-e0673df81ab3");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("08a13a0b-7c29-4e03-a6dc-5e1a67a62b7d");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("cedbab27-879e-453c-9a1a-a04ae10de13d");
    private static final UUID PASSPORT_ID =
            UUID.fromString("aa0c909b-e9ec-48e0-b2c3-eef4ef6f6563");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CURRENT_TIME = Instant.parse("2026-07-21T00:00:00Z");

    private final ManagePublicShareService service;
    private final PublicShareRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ManagePublicShareServiceIntegrationTest(
            ManagePublicShareService service,
            PublicShareRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.service = service;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        jdbcTemplate.update("DELETE FROM public_shares");
        jdbcTemplate.update("DELETE FROM outbox_reprocess_audit");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "share-service-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "share-service-other@example.com");
        insertPassport();
    }

    @Test
    @DisplayName("현재 보유자가 7일 SUMMARY share를 발급한다")
    void issuesShareForCurrentHolder() {
        IssuedPublicShare issuedShare = service.issue(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.SUMMARY
        );

        assertThat(issuedShare.rawToken()).hasSize(43);
        assertThat(issuedShare.publicShare().expiresAt())
                .isEqualTo(CURRENT_TIME.plus(7, ChronoUnit.DAYS));
        assertThat(repository.findByTokenHash(issuedShare.publicShare().tokenHash()))
                .contains(issuedShare.publicShare());
    }

    @Test
    @DisplayName("사용 중인 share가 있으면 일반 발급을 거부한다")
    void rejectsSecondActiveShare() {
        service.issue(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.SUMMARY
        );

        assertThatThrownBy(() -> service.issue(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.FILE_MATCH
        )).isInstanceOfSatisfying(
                PublicShareException.class,
                exception -> assertThat(exception.code())
                        .isEqualTo(PublicShareErrorCode.PUBLIC_SHARE_ALREADY_ACTIVE)
        );
    }

    @Test
    @DisplayName("rotation은 기존 share를 폐기하고 새 raw token을 한 번 발급한다")
    void rotatesShare() {
        IssuedPublicShare previousShare = service.issue(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.SUMMARY
        );

        IssuedPublicShare rotatedShare = service.rotate(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.FILE_MATCH
        );

        assertThat(rotatedShare.rawToken()).isNotEqualTo(previousShare.rawToken());
        assertThat(rotatedShare.publicShare().capability())
                .isEqualTo(PublicShareCapability.FILE_MATCH);
        assertThat(repository.findByTokenHash(previousShare.publicShare().tokenHash()))
                .hasValueSatisfying(share -> assertThat(share.revokedAt())
                        .isEqualTo(CURRENT_TIME));
    }

    @Test
    @DisplayName("현재 보유자가 share를 폐기한다")
    void revokesShare() {
        IssuedPublicShare issuedShare = service.issue(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.SUMMARY
        );

        service.revoke(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                issuedShare.publicShare().id()
        );

        assertThat(repository.findByTokenHash(issuedShare.publicShare().tokenHash()))
                .hasValueSatisfying(share -> assertThat(share.revokedAt())
                        .isEqualTo(CURRENT_TIME));
    }

    @Test
    @DisplayName("현재 보유자가 아닌 계정에는 Passport 존재도 공개하지 않는다")
    void hidesPassportFromOtherAccount() {
        assertThatThrownBy(() -> service.issue(
                OTHER_ACCOUNT_ID,
                PASSPORT_ID,
                PublicShareCapability.SUMMARY
        )).isInstanceOfSatisfying(
                PublicShareException.class,
                exception -> assertThat(exception.code())
                        .isEqualTo(PublicShareErrorCode.PUBLIC_SHARE_NOT_FOUND)
        );
    }

    private void insertAccount(UUID accountId, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                """,
                accountId,
                email,
                BCRYPT_HASH,
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
    }

    private void insertPassport() {
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at, confirmed_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'CONFIRMED', ?, ?, ?)
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                CURRENT_TIME.atOffset(ZoneOffset.UTC),
                CURRENT_TIME.atOffset(ZoneOffset.UTC),
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
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
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
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
                OWNER_ACCOUNT_ID,
                CURRENT_TIME.atOffset(ZoneOffset.UTC)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);
        }
    }
}
