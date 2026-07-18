package com.naesan.passport.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.OutboxClaimRequest;
import com.naesan.passport.application.OutboxProcessingException;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.support.ControllableProofAnchorAdapter;

@SpringBootTest
@Import({
        TestcontainersConfiguration.class,
        ProcessProofOutboxIntegrationTest.RecordingProofConfiguration.class
})
class ProofOutboxConcurrencyIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("b603326c-d1a5-44b9-ad57-b8161b0c7c57");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final int EVENT_COUNT = 100;
    private static final int WORKER_COUNT = 4;

    private final IssuePassportService issuePassportService;
    private final ProcessProofOutboxService processProofOutboxService;
    private final OutboxEventRepository outboxEventRepository;
    private final ControllableProofAnchorAdapter proofAnchorPort;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ProofOutboxConcurrencyIntegrationTest(
            IssuePassportService issuePassportService,
            ProcessProofOutboxService processProofOutboxService,
            OutboxEventRepository outboxEventRepository,
            ControllableProofAnchorAdapter proofAnchorPort,
            JdbcTemplate jdbcTemplate
    ) {
        this.issuePassportService = issuePassportService;
        this.processProofOutboxService = processProofOutboxService;
        this.outboxEventRepository = outboxEventRepository;
        this.proofAnchorPort = proofAnchorPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareOwner() {
        proofAnchorPort.reset();
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
                VALUES (?, 'proof-concurrency@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("claim 뒤 종료된 worker의 event를 lease 만료 후 새 worker가 회수한다")
    void recoversEventAfterClaimingWorkerStops() throws InterruptedException {
        issuePassports(1);
        outboxEventRepository.claimNextDue(
                new OutboxClaimRequest(
                        "stopped-worker",
                        UUID.randomUUID(),
                        Duration.ofMillis(50)
                )
        ).orElseThrow();

        awaitLeaseExpiration();
        boolean recovered = processProofOutboxService.processNext(
                "restarted-worker"
        );

        assertThat(recovered).isTrue();
        assertThat(countOutboxStatus("SUCCEEDED")).isOne();
        assertThat(singleAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("lease를 잃은 worker는 새 worker의 완료 결과를 덮어쓰지 못한다")
    void rejectsStaleWorkerFinalize()
            throws ExecutionException, InterruptedException {
        issuePassports(1);
        proofAnchorPort.blockNextSubmission();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> staleWorker = executor.submit(() ->
                    processProofOutboxService.processNext("stale-worker")
            );
            proofAnchorPort.awaitBlockedSubmission();
            expireCurrentLease();

            boolean recovered = processProofOutboxService.processNext(
                    "current-worker"
            );
            proofAnchorPort.releaseBlockedSubmission();

            assertThat(recovered).isTrue();
            assertThatThrownBy(staleWorker::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OutboxProcessingException.class);
        }

        assertThat(countOutboxStatus("SUCCEEDED")).isOne();
        assertThat(countProofState("CONFIRMED")).isOne();
        assertThat(proofAnchorPort.submitCount()).isEqualTo(2);
        assertThat(proofAnchorPort.storedReceiptCount()).isOne();
    }

    @Test
    @DisplayName("네 worker가 100개 event를 중복 claim 없이 모두 완료한다")
    void processesOneHundredEventsWithFourWorkers()
            throws ExecutionException, InterruptedException {
        issuePassports(EVENT_COUNT);
        CountDownLatch workersReady = new CountDownLatch(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            List<Future<Integer>> processedCounts = IntStream
                    .range(0, WORKER_COUNT)
                    .mapToObj(workerIndex -> executor.submit(() ->
                            processUntilEmpty(
                                    "worker-" + workerIndex,
                                    workersReady,
                                    start
                            )
                    ))
                    .toList();

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int processedCount = 0;
            for (Future<Integer> processed : processedCounts) {
                processedCount += processed.get();
            }
            assertThat(processedCount).isEqualTo(EVENT_COUNT);
        }

        assertThat(countOutboxStatus("SUCCEEDED")).isEqualTo(EVENT_COUNT);
        assertThat(countProofState("CONFIRMED")).isEqualTo(EVENT_COUNT);
        assertThat(proofAnchorPort.submitCount()).isEqualTo(EVENT_COUNT);
        assertThat(proofAnchorPort.storedReceiptCount()).isEqualTo(EVENT_COUNT);
    }

    private int processUntilEmpty(
            String workerId,
            CountDownLatch workersReady,
            CountDownLatch start
    ) throws InterruptedException {
        workersReady.countDown();
        start.await();
        int processedCount = 0;
        while (processProofOutboxService.processNext(workerId)) {
            processedCount++;
        }
        return processedCount;
    }

    private void issuePassports(int eventCount) {
        for (int index = 0; index < eventCount; index++) {
            UUID evidenceId = UUID.randomUUID();
            UUID snapshotId = UUID.randomUUID();
            insertConfirmedEvidence(evidenceId, snapshotId, index);
            issuePassportService.issue(OWNER_ACCOUNT_ID, snapshotId);
        }
    }

    private void insertConfirmedEvidence(
            UUID evidenceId,
            UUID snapshotId,
            int index
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, version, created_at, updated_at, confirmed_at
                )
                VALUES (
                    ?, ?, '생각상점', ?, ?,
                    ?, 'KRW', 'CONFIRMED', 1, ?, ?, ?
                )
                """,
                evidenceId,
                OWNER_ACCOUNT_ID,
                "생각등대-" + index,
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
                snapshotId,
                evidenceId,
                "{}".getBytes(StandardCharsets.UTF_8),
                "%064x".formatted(index),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    private void awaitLeaseExpiration() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (currentLeaseIsActive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(currentLeaseIsActive()).isFalse();
    }

    private boolean currentLeaseIsActive() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT lease_until > clock_timestamp() FROM outbox_events",
                Boolean.class
        ));
    }

    private void expireCurrentLease() {
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET lease_until = clock_timestamp() - INTERVAL '1 second'
                """
        );
    }

    private int countOutboxStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = ?",
                Integer.class,
                status
        );
    }

    private int countProofState(String state) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proof_anchors WHERE state = ?",
                Integer.class,
                state
        );
    }

    private int singleAttemptCount() {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_events",
                Integer.class
        );
    }
}
