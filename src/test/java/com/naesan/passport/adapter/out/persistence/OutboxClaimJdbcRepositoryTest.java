package com.naesan.passport.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.OutboxClaimRequest;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OutboxEventStatus;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        OutboxEventJdbcRepository.class
})
class OutboxClaimJdbcRepositoryTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("f0c7490a-a8e1-42a8-ad76-f53d8bacf849");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("8c8c1180-f214-4a6f-b1c0-ceb4f4ce09f5");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("2fc1bbe7-d21a-463b-8ae1-a3744f19f324");
    private static final UUID PASSPORT_ID =
            UUID.fromString("0db326b9-311b-478f-a8d3-fb0d8e500117");
    private static final UUID PROOF_ANCHOR_ID =
            UUID.fromString("144f78a9-a7f2-4703-a042-e42c56bd9ee9");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final OutboxEventJdbcRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxClaimJdbcRepositoryTest(
            OutboxEventJdbcRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareProofAnchor() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'outbox-claim@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, version, created_at, updated_at, confirmed_at
                )
                VALUES (
                    ?, ?, '생각상점', '생각등대', ?,
                    ?, 'KRW', 'CONFIRMED', 1, ?, ?, ?
                )
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
        jdbcTemplate.update(
                """
                INSERT INTO proof_anchors (
                    id, passport_id, schema_version, anchor_salt,
                    commitment, state, created_at, updated_at
                )
                VALUES (?, ?, 1, ?, ?, 'PREPARED', ?, ?)
                """,
                PROOF_ANCHOR_ID,
                PASSPORT_ID,
                new byte[32],
                new byte[32],
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("DB clock 기준 due PENDING event를 한 번만 claim한다")
    void claimsDuePendingEventOnce() {
        OutboxEvent pendingEvent = pendingEvent(CREATED_AT);
        repository.save(pendingEvent);
        UUID claimToken = UUID.randomUUID();

        assertThat(repository.claimNextDue(new OutboxClaimRequest(
                "worker-1",
                claimToken,
                Duration.ofSeconds(30)
        )))
                .get()
                .satisfies(claim -> {
                    assertThat(claim.event().id()).isEqualTo(pendingEvent.id());
                    assertThat(claim.event().status())
                            .isEqualTo(OutboxEventStatus.CLAIMED);
                    assertThat(claim.event().attemptCount()).isOne();
                    assertThat(claim.claimToken()).isEqualTo(claimToken);
                    assertThat(claim.fencingVersion()).isOne();
                });
        assertThat(repository.claimNextDue(request("worker-2"))).isEmpty();
    }

    private OutboxClaimRequest request(String workerId) {
        return new OutboxClaimRequest(
                workerId,
                UUID.randomUUID(),
                Duration.ofSeconds(30)
        );
    }

    @Test
    @DisplayName("아직 due가 아닌 event는 claim하지 않는다")
    void skipsEventBeforeDueTime() {
        repository.save(pendingEvent(Instant.parse("2099-01-01T00:00:00Z")));

        assertThat(repository.claimNextDue(request("worker-1"))).isEmpty();
    }

    @Test
    @DisplayName("만료된 claim은 새 token과 증가한 fencing version으로 회수한다")
    void recoversExpiredClaim() {
        repository.save(pendingEvent(CREATED_AT));
        var firstClaim = repository.claimNextDue(request("worker-1")).orElseThrow();
        jdbcTemplate.update(
                "UPDATE outbox_events SET lease_until = clock_timestamp() - INTERVAL '1 second'"
        );

        var recoveredClaim = repository.claimNextDue(request("worker-2")).orElseThrow();

        assertThat(recoveredClaim.claimToken()).isNotEqualTo(firstClaim.claimToken());
        assertThat(recoveredClaim.fencingVersion()).isEqualTo(2);
        assertThat(recoveredClaim.claimedBy()).isEqualTo("worker-2");
    }

    @Test
    @DisplayName("회수 전 stale claim은 새 claimant의 event를 완료할 수 없다")
    void rejectsStaleClaimCompletion() {
        repository.save(pendingEvent(CREATED_AT));
        var staleClaim = repository.claimNextDue(request("worker-1")).orElseThrow();
        jdbcTemplate.update(
                "UPDATE outbox_events SET lease_until = clock_timestamp() - INTERVAL '1 second'"
        );
        var currentClaim = repository.claimNextDue(request("worker-2")).orElseThrow();
        Instant completedAt = Instant.now();

        boolean staleCompleted = repository.completeClaimed(
                staleClaim,
                staleClaim.event().succeed(completedAt)
        );
        boolean currentCompleted = repository.completeClaimed(
                currentClaim,
                currentClaim.event().succeed(completedAt)
        );

        assertThat(staleCompleted).isFalse();
        assertThat(currentCompleted).isTrue();
    }

    private OutboxEvent pendingEvent(Instant nextAttemptAt) {
        return OutboxEvent.restore(
                UUID.randomUUID(),
                OutboxEvent.PROOF_ANCHOR_REQUESTED,
                PASSPORT_ID,
                PROOF_ANCHOR_ID,
                1,
                "{\"schemaVersion\":1,\"commitment\":\"" + "a".repeat(64) + "\"}",
                "proof-anchor:" + UUID.randomUUID(),
                OutboxEventStatus.PENDING,
                0,
                nextAttemptAt,
                CREATED_AT,
                CREATED_AT
        );
    }
}
