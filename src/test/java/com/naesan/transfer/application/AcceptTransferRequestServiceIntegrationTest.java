package com.naesan.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.naesan.TestcontainersConfiguration;
import com.naesan.share.application.port.out.PublicShareRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AcceptTransferRequestServiceIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("321d3661-8510-4c05-b8b8-b74221799d2d");
    private static final UUID RECIPIENT_ACCOUNT_ID =
            UUID.fromString("90325ff8-9ee7-450f-80bc-bdb7f39959fe");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("2cc6fd62-607c-4ebd-93b9-6fd28bef6837");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("c96eae1d-4e8f-465d-92c9-414817e54a41");
    private static final UUID PASSPORT_ID =
            UUID.fromString("f3512b2b-67b4-4e18-a46d-bc1e5f37fb0f");
    private static final UUID TRANSFER_REQUEST_ID =
            UUID.fromString("891442cf-a714-4584-b4c3-5bd59956df2b");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-22T00:00:00Z");

    private final AcceptTransferRequestService service;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PublicShareRepository publicShareRepository;

    @Autowired
    AcceptTransferRequestServiceIntegrationTest(
            AcceptTransferRequestService service,
            JdbcTemplate jdbcTemplate
    ) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareTransferRequest() {
        jdbcTemplate.update("DELETE FROM transfer_requests");
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
        insertAccount(OWNER_ACCOUNT_ID, "accept-owner@example.com");
        insertAccount(RECIPIENT_ACCOUNT_ID, "accept-recipient@example.com");
        insertPassportGraph();
        insertPublicShare();
        insertTransferRequest();
    }

    @AfterEach
    void removeTransferRequest() {
        jdbcTemplate.update("DELETE FROM transfer_requests");
    }

    @Test
    @DisplayName("recipient 수락은 holder·request·history·share를 함께 변경한다")
    void acceptsTransferRequestAtomically() {
        service.accept(RECIPIENT_ACCOUNT_ID, TRANSFER_REQUEST_ID);

        assertThat(currentHolderAccountId()).isEqualTo(RECIPIENT_ACCOUNT_ID);
        assertThat(passportVersion()).isOne();
        assertThat(transferStatus()).isEqualTo("ACCEPTED");
        assertThat(transferVersion()).isOne();
        assertThat(transferredHistoryCount()).isOne();
        assertThat(activeShareCount()).isZero();
    }

    @Test
    @DisplayName("share 폐기에 실패하면 accept의 앞선 DB 변경도 모두 rollback한다")
    void rollsBackWhenShareRevocationFails() {
        doThrow(new IllegalStateException("share revoke failure"))
                .when(publicShareRepository)
                .revokeAllByPassportId(eq(PASSPORT_ID), any(Instant.class));

        assertThatThrownBy(() -> service.accept(
                RECIPIENT_ACCOUNT_ID,
                TRANSFER_REQUEST_ID
        )).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("share revoke failure");

        assertThat(currentHolderAccountId()).isEqualTo(OWNER_ACCOUNT_ID);
        assertThat(passportVersion()).isZero();
        assertThat(transferStatus()).isEqualTo("PENDING");
        assertThat(transferVersion()).isZero();
        assertThat(transferredHistoryCount()).isZero();
        assertThat(activeShareCount()).isOne();
    }

    private UUID currentHolderAccountId() {
        return jdbcTemplate.queryForObject(
                "SELECT current_holder_account_id FROM passports WHERE id = ?",
                UUID.class,
                PASSPORT_ID
        );
    }

    private Long passportVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM passports WHERE id = ?",
                Long.class,
                PASSPORT_ID
        );
    }

    private String transferStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer_requests WHERE id = ?",
                String.class,
                TRANSFER_REQUEST_ID
        );
    }

    private Long transferVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM transfer_requests WHERE id = ?",
                Long.class,
                TRANSFER_REQUEST_ID
        );
    }

    private Integer transferredHistoryCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ownership_history
                WHERE passport_id = ? AND reason = 'TRANSFERRED'
                """,
                Integer.class,
                PASSPORT_ID
        );
    }

    private Integer activeShareCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public_shares
                WHERE passport_id = ? AND revoked_at IS NULL
                """,
                Integer.class,
                PASSPORT_ID
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
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    private void insertPassportGraph() {
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
                offset(CREATED_AT),
                offset(CREATED_AT),
                offset(CREATED_AT)
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
                offset(CREATED_AT)
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
                offset(CREATED_AT)
        );
        jdbcTemplate.update(
                """
                INSERT INTO ownership_history (
                    id, passport_id, new_holder_account_id, reason, changed_at
                )
                VALUES (?, ?, ?, 'ISSUED', ?)
                """,
                UUID.randomUUID(),
                PASSPORT_ID,
                OWNER_ACCOUNT_ID,
                offset(CREATED_AT)
        );
    }

    private OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private void insertPublicShare() {
        jdbcTemplate.update(
                """
                INSERT INTO public_shares (
                    id, passport_id, token_hash, capability,
                    expires_at, revoked_at, created_at
                )
                VALUES (?, ?, ?, 'SUMMARY', ?, NULL, ?)
                """,
                UUID.randomUUID(),
                PASSPORT_ID,
                new byte[32],
                offset(CREATED_AT.plus(7, ChronoUnit.DAYS)),
                offset(CREATED_AT)
        );
    }

    private void insertTransferRequest() {
        jdbcTemplate.update(
                """
                INSERT INTO transfer_requests (
                    id, passport_id, requester_account_id, recipient_account_id,
                    status, version, expires_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                """,
                TRANSFER_REQUEST_ID,
                PASSPORT_ID,
                OWNER_ACCOUNT_ID,
                RECIPIENT_ACCOUNT_ID,
                offset(CREATED_AT.plus(7, ChronoUnit.DAYS)),
                offset(CREATED_AT),
                offset(CREATED_AT)
        );
    }
}
