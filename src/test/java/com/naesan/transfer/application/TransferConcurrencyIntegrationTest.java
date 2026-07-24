package com.naesan.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.port.out.PassportRepository;

@SpringBootTest
@Import({
        TestcontainersConfiguration.class,
        TransferConcurrencyIntegrationTest.FixedClockConfiguration.class
})
class TransferConcurrencyIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("094c4450-1663-4bd7-a123-cc1477e7e25e");
    private static final UUID RECIPIENT_ACCOUNT_ID =
            UUID.fromString("a8eea37f-d8dd-4533-973d-c6f7234eed60");
    private static final UUID NEXT_RECIPIENT_ACCOUNT_ID =
            UUID.fromString("ce37be4d-a591-4439-b5af-23e59ac51cc9");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("b0478fac-ecfe-4a37-8cf9-af4ed3311821");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("153e43bd-af0e-4330-8642-5bf2f5b49023");
    private static final UUID PASSPORT_ID =
            UUID.fromString("ec84115d-eb46-4536-a59d-66d491024132");
    private static final UUID TRANSFER_REQUEST_ID =
            UUID.fromString("ec25dc8e-5545-46e4-a10c-214344646922");
    private static final String NEXT_RECIPIENT_EMAIL =
            "next-recipient@example.com";
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-22T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final Instant BEFORE_EXPIRY =
            EXPIRES_AT.minusMillis(1);
    private static final Instant AFTER_EXPIRY =
            EXPIRES_AT.plusMillis(1);

    private final CreateTransferRequestService createService;
    private final AcceptTransferRequestService acceptService;
    private final JdbcTemplate jdbcTemplate;
    private final MutableClock clock;

    @MockitoSpyBean
    private PassportRepository passportRepository;

    @Autowired
    TransferConcurrencyIntegrationTest(
            CreateTransferRequestService createService,
            AcceptTransferRequestService acceptService,
            JdbcTemplate jdbcTemplate,
            MutableClock clock
    ) {
        this.createService = createService;
        this.acceptService = acceptService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @BeforeEach
    void prepareExpiredTransferRequest() {
        deleteAll();
        insertAccount(OWNER_ACCOUNT_ID, "race-owner@example.com");
        insertAccount(RECIPIENT_ACCOUNT_ID, "race-recipient@example.com");
        insertAccount(NEXT_RECIPIENT_ACCOUNT_ID, NEXT_RECIPIENT_EMAIL);
        insertPassportGraph();
        insertExpiredTransferRequest();
    }

    @RepeatedTest(10)
    @DisplayName("만료 경계의 accept와 새 요청은 같은 잠금 순서로 직렬화된다")
    void serializesExpiryBoundaryRace(TestReporter testReporter) throws Exception {
        CountDownLatch createLockedPassport = new CountDownLatch(1);
        CountDownLatch acceptWaitingForPassport = new CountDownLatch(1);
        CountDownLatch continueCreate = new CountDownLatch(1);
        blockFirstPassportLock(
                createLockedPassport,
                acceptWaitingForPassport,
                continueCreate
        );
        clock.set(BEFORE_EXPIRY);

        List<RaceOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RaceOutcome> createAttempt =
                    executor.submit(this::createNextRequest);
            assertThat(createLockedPassport.await(10, TimeUnit.SECONDS))
                    .isTrue();

            Future<RaceOutcome> acceptAttempt =
                    executor.submit(this::acceptExpiredRequest);
            assertThat(acceptWaitingForPassport.await(10, TimeUnit.SECONDS))
                    .isTrue();
            clock.set(AFTER_EXPIRY);
            continueCreate.countDown();
            outcomes = List.of(
                    createAttempt.get(20, TimeUnit.SECONDS),
                    acceptAttempt.get(20, TimeUnit.SECONDS)
            );
        } finally {
            continueCreate.countDown();
        }

        testReporter.publishEntry(Map.of(
                "createOutcome", outcomes.get(0).name(),
                "acceptOutcome", outcomes.get(1).name()
        ));
        assertThat(outcomes).containsExactly(
                RaceOutcome.CREATED,
                RaceOutcome.NOT_PENDING
        );
        assertThat(transferStatus(TRANSFER_REQUEST_ID)).isEqualTo("EXPIRED");
        assertThat(pendingRequestCount()).isOne();
        assertThat(pendingRecipientAccountId())
                .isEqualTo(NEXT_RECIPIENT_ACCOUNT_ID);
        assertThat(currentHolderAccountId()).isEqualTo(OWNER_ACCOUNT_ID);
        assertThat(transferredHistoryCount()).isZero();
    }

    private void blockFirstPassportLock(
            CountDownLatch firstPassportLocked,
            CountDownLatch secondAttemptWaiting,
            CountDownLatch continueFirstAttempt
    ) {
        AtomicInteger invocationCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (invocationCount.incrementAndGet() == 1) {
                Object result = invocation.callRealMethod();
                firstPassportLocked.countDown();
                await(continueFirstAttempt);
                return result;
            }
            secondAttemptWaiting.countDown();
            return invocation.callRealMethod();
        }).when(passportRepository).findByIdForUpdate(eq(PASSPORT_ID));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 baseline 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 baseline 대기가 중단되었습니다.", exception);
        }
    }

    @Test
    @DisplayName("만료 직전 accept가 먼저 잠그면 새 요청 없이 이전을 완료한다")
    void completesAcceptanceThatWinsExpiryBoundaryRace() throws Exception {
        CountDownLatch acceptLockedPassport = new CountDownLatch(1);
        CountDownLatch createWaitingForPassport = new CountDownLatch(1);
        CountDownLatch continueAccept = new CountDownLatch(1);
        blockFirstPassportLock(
                acceptLockedPassport,
                createWaitingForPassport,
                continueAccept
        );
        clock.set(BEFORE_EXPIRY);

        List<RaceOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RaceOutcome> acceptAttempt =
                    executor.submit(this::acceptExpiredRequest);
            assertThat(acceptLockedPassport.await(10, TimeUnit.SECONDS))
                    .isTrue();

            Future<RaceOutcome> createAttempt =
                    executor.submit(this::createAfterOwnershipTransfer);
            assertThat(createWaitingForPassport.await(10, TimeUnit.SECONDS))
                    .isTrue();

            continueAccept.countDown();
            outcomes = List.of(
                    acceptAttempt.get(20, TimeUnit.SECONDS),
                    createAttempt.get(20, TimeUnit.SECONDS)
            );
        } finally {
            continueAccept.countDown();
        }

        assertThat(outcomes).containsExactly(
                RaceOutcome.ACCEPTED,
                RaceOutcome.NOT_OWNER
        );
        assertThat(transferStatus(TRANSFER_REQUEST_ID)).isEqualTo("ACCEPTED");
        assertThat(pendingRequestCount()).isZero();
        assertThat(currentHolderAccountId()).isEqualTo(RECIPIENT_ACCOUNT_ID);
        assertThat(transferredHistoryCount()).isOne();
    }

    private RaceOutcome createAfterOwnershipTransfer() {
        try {
            createService.create(
                    OWNER_ACCOUNT_ID,
                    PASSPORT_ID,
                    NEXT_RECIPIENT_EMAIL
            );
            return RaceOutcome.CREATED;
        } catch (TransferException exception) {
            assertThat(exception.code())
                    .isEqualTo(TransferErrorCode.TRANSFER_NOT_FOUND);
            return RaceOutcome.NOT_OWNER;
        }
    }

    private RaceOutcome createNextRequest() {
        createService.create(
                OWNER_ACCOUNT_ID,
                PASSPORT_ID,
                NEXT_RECIPIENT_EMAIL
        );
        return RaceOutcome.CREATED;
    }

    private RaceOutcome acceptExpiredRequest() {
        try {
            acceptService.accept(
                    RECIPIENT_ACCOUNT_ID,
                    TRANSFER_REQUEST_ID
            );
            return RaceOutcome.ACCEPTED;
        } catch (TransferException exception) {
            assertThat(exception.code())
                    .isEqualTo(TransferErrorCode.TRANSFER_NOT_PENDING);
            return RaceOutcome.NOT_PENDING;
        }
    }

    private String transferStatus(UUID requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer_requests WHERE id = ?",
                String.class,
                requestId
        );
    }

    private Integer pendingRequestCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transfer_requests
                WHERE passport_id = ? AND status = 'PENDING'
                """,
                Integer.class,
                PASSPORT_ID
        );
    }

    private UUID pendingRecipientAccountId() {
        return jdbcTemplate.queryForObject(
                """
                SELECT recipient_account_id
                FROM transfer_requests
                WHERE passport_id = ? AND status = 'PENDING'
                """,
                UUID.class,
                PASSPORT_ID
        );
    }

    private UUID currentHolderAccountId() {
        return jdbcTemplate.queryForObject(
                "SELECT current_holder_account_id FROM passports WHERE id = ?",
                UUID.class,
                PASSPORT_ID
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

    private void deleteAll() {
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
                offset(CREATED_AT)
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

    private void insertExpiredTransferRequest() {
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
                offset(EXPIRES_AT),
                offset(CREATED_AT),
                offset(CREATED_AT)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BEFORE_EXPIRY);
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> currentTime;

        private MutableClock(Instant currentTime) {
            this.currentTime = new AtomicReference<>(currentTime);
        }

        void set(Instant currentTime) {
            this.currentTime.set(currentTime);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("테스트 시계는 UTC만 지원합니다.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime.get();
        }
    }

    private enum RaceOutcome {
        CREATED,
        ACCEPTED,
        NOT_PENDING,
        NOT_OWNER
    }
}
