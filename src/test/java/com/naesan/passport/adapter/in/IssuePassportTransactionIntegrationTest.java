package com.naesan.passport.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.naesan.passport.adapter.out.persistence.OutboxEventJdbcRepository;
import com.naesan.passport.adapter.out.security.SecureRandomAnchorSaltGenerator;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.OutboxClaimRequest;
import com.naesan.passport.application.PassportErrorCode;
import com.naesan.passport.application.PassportException;
import com.naesan.passport.application.port.out.AnchorSaltGenerator;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OutboxClaim;

@SpringBootTest
@Import({
        TestcontainersConfiguration.class,
        IssuePassportTransactionIntegrationTest.FailureInjectionConfiguration.class
})
class IssuePassportTransactionIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("d22891dc-2bfb-4cce-8a29-ccf757ba1936");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("46a05c8c-08be-45a5-bb5c-bc897e26b2fd");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("4d478c00-ac4c-4436-9f77-ad7c1c49a0f5");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final IssuePassportService issuePassportService;
    private final FailureInjectingOutboxEventRepository outboxEventRepository;
    private final SynchronizingAnchorSaltGenerator anchorSaltGenerator;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    IssuePassportTransactionIntegrationTest(
            IssuePassportService issuePassportService,
            FailureInjectingOutboxEventRepository outboxEventRepository,
            SynchronizingAnchorSaltGenerator anchorSaltGenerator,
            JdbcTemplate jdbcTemplate
    ) {
        this.issuePassportService = issuePassportService;
        this.outboxEventRepository = outboxEventRepository;
        this.anchorSaltGenerator = anchorSaltGenerator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareConfirmedSnapshot() {
        outboxEventRepository.allowSave();
        anchorSaltGenerator.disableSynchronization();
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'passport-transaction@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
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
                OWNER_ACCOUNT_ID,
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
    @DisplayName("Passport와 소유 이력, proof, outbox를 한 transaction으로 저장한다")
    void commitsIssuanceGraphTogether() {
        issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID);

        assertThat(rowCounts()).containsExactly(1, 1, 1, 1);
    }

    @Test
    @DisplayName("Outbox 저장 실패 시 앞서 저장한 Passport와 proof를 모두 rollback한다")
    void rollsBackEntireIssuanceWhenOutboxSaveFails() {
        outboxEventRepository.failSave();

        assertThatThrownBy(() ->
                issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID)
        ).isInstanceOf(InjectedOutboxFailure.class);

        assertThat(rowCounts()).containsExactly(0, 0, 0, 0);
    }

    @Test
    @DisplayName("같은 snapshot의 동시 발급은 Passport 한 건만 commit한다")
    void commitsOnePassportForConcurrentIssuance() throws Exception {
        anchorSaltGenerator.synchronizeTwoRequests();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() ->
                    issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID));
            Future<?> second = executor.submit(() ->
                    issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID));

            List<Future<?>> requests = List.of(first, second);
            long successCount = requests.stream()
                    .filter(this::completedSuccessfully)
                    .count();
            long conflictCount = requests.stream()
                    .filter(this::failedWithIssuanceConflict)
                    .count();

            assertThat(successCount).isOne();
            assertThat(conflictCount).isOne();
        }

        assertThat(rowCounts()).containsExactly(1, 1, 1, 1);
    }

    private boolean completedSuccessfully(Future<?> request) {
        try {
            request.get();
            return true;
        } catch (ExecutionException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 발급 결과 대기가 중단되었습니다.", exception);
        }
    }

    private boolean failedWithIssuanceConflict(Future<?> request) {
        try {
            request.get();
            return false;
        } catch (ExecutionException exception) {
            return exception.getCause() instanceof PassportException passportException
                    && passportException.code()
                    == PassportErrorCode.PASSPORT_ALREADY_ISSUED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 발급 결과 대기가 중단되었습니다.", exception);
        }
    }

    private List<Integer> rowCounts() {
        return List.of(
                countRows("passports"),
                countRows("ownership_history"),
                countRows("proof_anchors"),
                countRows("outbox_events")
        );
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        FailureInjectingOutboxEventRepository failureInjectingOutboxEventRepository(
                OutboxEventJdbcRepository delegate
        ) {
            return new FailureInjectingOutboxEventRepository(delegate);
        }

        @Bean
        @Primary
        SynchronizingAnchorSaltGenerator synchronizingAnchorSaltGenerator(
                SecureRandomAnchorSaltGenerator delegate
        ) {
            return new SynchronizingAnchorSaltGenerator(delegate);
        }
    }

    static final class FailureInjectingOutboxEventRepository
            implements OutboxEventRepository {
        private final OutboxEventJdbcRepository delegate;
        private boolean failing;

        FailureInjectingOutboxEventRepository(OutboxEventJdbcRepository delegate) {
            this.delegate = delegate;
        }

        void allowSave() {
            failing = false;
        }

        void failSave() {
            failing = true;
        }

        @Override
        public void save(OutboxEvent outboxEvent) {
            if (failing) {
                throw new InjectedOutboxFailure();
            }
            delegate.save(outboxEvent);
        }

        @Override
        public Optional<OutboxEvent> findById(UUID outboxEventId) {
            return delegate.findById(outboxEventId);
        }

        @Override
        public Optional<OutboxEvent> findByProofAnchorId(UUID proofAnchorId) {
            return delegate.findByProofAnchorId(proofAnchorId);
        }

        @Override
        public Optional<OutboxClaim> claimNextDue(OutboxClaimRequest request) {
            return delegate.claimNextDue(request);
        }

        @Override
        public boolean completeClaimed(OutboxEvent succeededEvent) {
            return delegate.completeClaimed(succeededEvent);
        }
    }

    static final class SynchronizingAnchorSaltGenerator implements AnchorSaltGenerator {
        private final SecureRandomAnchorSaltGenerator delegate;
        private CyclicBarrier barrier;

        SynchronizingAnchorSaltGenerator(SecureRandomAnchorSaltGenerator delegate) {
            this.delegate = delegate;
        }

        void synchronizeTwoRequests() {
            barrier = new CyclicBarrier(2);
        }

        void disableSynchronization() {
            barrier = null;
        }

        @Override
        public byte[] generate() {
            byte[] anchorSalt = delegate.generate();
            if (barrier != null) {
                awaitConcurrentGeneration();
            }
            return anchorSalt;
        }

        private void awaitConcurrentGeneration() {
            try {
                barrier.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("동시 salt 생성 대기가 중단되었습니다.", exception);
            } catch (BrokenBarrierException exception) {
                throw new IllegalStateException("동시 salt 생성을 완료하지 못했습니다.", exception);
            }
        }
    }

    static final class InjectedOutboxFailure extends RuntimeException {
    }
}
