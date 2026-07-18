package com.naesan.evidence.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.StoredObjectMetadata;
import com.naesan.evidence.domain.StorageKey;

public class ReconcileOrphanEvidenceFilesService {
    private final EvidenceFileRepository evidenceFileRepository;
    private final FileStorage fileStorage;
    private final Clock clock;
    private final Duration minimumObjectAge;

    public ReconcileOrphanEvidenceFilesService(
            EvidenceFileRepository evidenceFileRepository,
            FileStorage fileStorage,
            Clock clock,
            Duration minimumObjectAge
    ) {
        this.evidenceFileRepository = Objects.requireNonNull(evidenceFileRepository);
        this.fileStorage = Objects.requireNonNull(fileStorage);
        this.clock = Objects.requireNonNull(clock);
        this.minimumObjectAge = requirePositive(minimumObjectAge);
    }

    private Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("orphan 정리 유예 시간은 양수여야 합니다.");
        }
        return duration;
    }

    public OrphanReconciliationResult reconcile() {
        Set<StorageKey> referencedKeys = evidenceFileRepository.findAllObjectKeys();
        Instant orphanCutoff = clock.instant().minus(minimumObjectAge);
        int scanned = 0;
        int deleted = 0;
        int failed = 0;

        for (StoredObjectMetadata storedObject : storedObjects()) {
            scanned++;
            if (!isOrphan(storedObject, referencedKeys, orphanCutoff)) {
                continue;
            }
            try {
                fileStorage.delete(storedObject.key());
                deleted++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        return new OrphanReconciliationResult(scanned, deleted, failed);
    }

    private Iterable<StoredObjectMetadata> storedObjects() {
        return Stream.concat(
                fileStorage.listTemporaryObjects().stream(),
                fileStorage.listPermanentObjects().stream()
        ).toList();
    }

    private boolean isOrphan(
            StoredObjectMetadata storedObject,
            Set<StorageKey> referencedKeys,
            Instant orphanCutoff
    ) {
        return !referencedKeys.contains(storedObject.key())
                && !storedObject.lastModifiedAt().isAfter(orphanCutoff);
    }
}
