package com.naesan.transfer.adapter.out.persistence;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.transfer.domain.TransferRequest;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        TransferRequestJdbcRepository.class
})
class TransferRequestJdbcRepositoryTest {
    private static final UUID REQUESTER_ACCOUNT_ID =
            UUID.fromString("061a5f3e-4f25-4385-8142-9f0d5c5b311a");
    private static final UUID RECIPIENT_ACCOUNT_ID =
            UUID.fromString("4fc90e59-d47c-407f-b8b4-42c2977d82a2");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("ec951955-e9db-4d0d-aa5e-7d67853421fd");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("cebb2f5d-b293-4e3e-a4a8-2fb5769b6c7b");
    private static final UUID PASSPORT_ID =
            UUID.fromString("09092a5d-a248-4c10-9695-cc9237361e3e");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-22T00:00:00Z");

    private final TransferRequestJdbcRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TransferRequestJdbcRepositoryTest(
            TransferRequestJdbcRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        insertAccount(REQUESTER_ACCOUNT_ID, "transfer-requester@example.com");
        insertAccount(RECIPIENT_ACCOUNT_ID, "transfer-recipient@example.com");
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at, confirmed_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'CONFIRMED', ?, ?, ?)
                """,
                EVIDENCE_ID,
                REQUESTER_ACCOUNT_ID,
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
                REQUESTER_ACCOUNT_ID,
                CREATED_AT.atOffset(ZoneOffset.UTC)
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

    @Test
    @DisplayName("이전 요청을 저장하고 참여 계정과 Passport로 조회한다")
    void storesAndFindsRequest() {
        TransferRequest request = createRequest(UUID.randomUUID());

        repository.save(request);

        assertThat(repository.findPendingByPassportId(PASSPORT_ID)).contains(request);
        assertThat(repository.findByIdForUpdate(request.id())).contains(request);
        assertThat(repository.findAllByRequesterAccountId(REQUESTER_ACCOUNT_ID))
                .containsExactly(request);
        assertThat(repository.findAllByRecipientAccountId(RECIPIENT_ACCOUNT_ID))
                .containsExactly(request);
    }

    @Test
    @DisplayName("상태와 version을 함께 갱신한다")
    void updatesRequest() {
        TransferRequest request = createRequest(UUID.randomUUID());
        repository.save(request);

        TransferRequest cancelledRequest = request.cancelBy(
                REQUESTER_ACCOUNT_ID,
                CREATED_AT.plus(1, ChronoUnit.HOURS)
        );
        repository.update(cancelledRequest);

        assertThat(repository.findByIdForUpdate(request.id()))
                .contains(cancelledRequest);
        assertThat(repository.findPendingByPassportId(PASSPORT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Passport마다 PENDING 요청을 하나만 저장한다")
    void preventsMultiplePendingRequests() {
        repository.save(createRequest(UUID.randomUUID()));

        assertThatThrownBy(() -> repository.save(createRequest(UUID.randomUUID())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private TransferRequest createRequest(UUID requestId) {
        return TransferRequest.create(
                requestId,
                PASSPORT_ID,
                REQUESTER_ACCOUNT_ID,
                RECIPIENT_ACCOUNT_ID,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        );
    }
}
