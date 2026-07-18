package com.naesan.evidence.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.evidence.application.AttachEvidenceFileService;
import com.naesan.evidence.application.ConfirmEvidenceService;
import com.naesan.evidence.application.EvidenceSnapshotCanonicalizer;
import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.StorageKey;

@SpringBootTest(properties =
        "naesan.storage.local.root=build/test-storage/confirm-concurrent")
@Import(TestcontainersConfiguration.class)
class ConcurrentConfirmEvidenceIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("81438fa5-03b5-4201-928d-35d2afdc9699");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("f76928f0-3afc-4887-961e-296f9a350ad3");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final byte[] PDF_CONTENT =
            "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8);
    private static final Path PERMANENT_DIRECTORY =
            Path.of("build/test-storage/confirm-concurrent/permanent");

    private final AttachEvidenceFileService attachService;
    private final PurchaseEvidenceRepository evidenceRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceSnapshotRepository snapshotRepository;
    private final FileStorage fileStorage;
    private final EvidenceSnapshotCanonicalizer canonicalizer;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    ConcurrentConfirmEvidenceIntegrationTest(
            AttachEvidenceFileService attachService,
            PurchaseEvidenceRepository evidenceRepository,
            EvidenceFileRepository evidenceFileRepository,
            EvidenceSnapshotRepository snapshotRepository,
            FileStorage fileStorage,
            EvidenceSnapshotCanonicalizer canonicalizer,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.attachService = attachService;
        this.evidenceRepository = evidenceRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.snapshotRepository = snapshotRepository;
        this.fileStorage = fileStorage;
        this.canonicalizer = canonicalizer;
        this.transactionManager = transactionManager;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @BeforeEach
    void prepareAttachedEvidence() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'concurrent-confirm@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                now.atOffset(ZoneOffset.UTC)
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
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
        attachService.attach(
                OWNER_ACCOUNT_ID,
                EVIDENCE_ID,
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        );
    }

    @Test
    @DisplayName("동시 확정 요청은 같은 snapshot을 반환하고 영구 파일 하나만 남긴다")
    void confirmsOnceUnderConcurrentRequests() throws Exception {
        long permanentObjectCount = permanentObjectCount();
        FileStorage synchronizedPromotionStorage = new SynchronizedPromotionStorage(
                fileStorage,
                new CyclicBarrier(2)
        );
        ConfirmEvidenceService confirmService = new ConfirmEvidenceService(
                evidenceRepository,
                evidenceFileRepository,
                snapshotRepository,
                synchronizedPromotionStorage,
                canonicalizer,
                new TransactionTemplate(transactionManager),
                clock
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<EvidenceSnapshot> first = executor.submit(() ->
                    confirmService.confirm(OWNER_ACCOUNT_ID, EVIDENCE_ID));
            Future<EvidenceSnapshot> second = executor.submit(() ->
                    confirmService.confirm(OWNER_ACCOUNT_ID, EVIDENCE_ID));

            assertThat(first.get()).isEqualTo(second.get());
        }

        assertThat(snapshotCount()).isEqualTo(1);
        assertThat(permanentObjectCount()).isEqualTo(permanentObjectCount + 1);
    }

    private long permanentObjectCount() throws Exception {
        if (Files.notExists(PERMANENT_DIRECTORY)) {
            return 0;
        }
        try (var paths = Files.list(PERMANENT_DIRECTORY)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private int snapshotCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_snapshots WHERE evidence_id = ?",
                Integer.class,
                EVIDENCE_ID
        );
    }

    private record SynchronizedPromotionStorage(
            FileStorage delegate,
            CyclicBarrier promotionBarrier
    ) implements FileStorage {

        @Override
        public StorageKey storeTemporary(InputStream content) {
            return delegate.storeTemporary(content);
        }

        @Override
        public InputStream open(StorageKey key) {
            return delegate.open(key);
        }

        @Override
        public StorageKey promote(StorageKey temporaryKey) {
            StorageKey permanentKey = delegate.promote(temporaryKey);
            awaitConcurrentPromotion();
            return permanentKey;
        }

        private void awaitConcurrentPromotion() {
            try {
                promotionBarrier.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FileStorageException(
                        "동시 승격 대기가 중단되었습니다.",
                        exception
                );
            } catch (BrokenBarrierException exception) {
                throw new FileStorageException(
                        "동시 승격 대기를 완료하지 못했습니다.",
                        exception
                );
            }
        }

        @Override
        public void delete(StorageKey key) {
            delegate.delete(key);
        }
    }
}
