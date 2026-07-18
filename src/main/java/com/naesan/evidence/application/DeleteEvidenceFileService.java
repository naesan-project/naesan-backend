package com.naesan.evidence.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;

public class DeleteEvidenceFileService {
    private final EvidenceFileRepository evidenceFileRepository;
    private final FileStorage fileStorage;
    private final Clock clock;

    public DeleteEvidenceFileService(
            EvidenceFileRepository evidenceFileRepository,
            FileStorage fileStorage,
            Clock clock
    ) {
        this.evidenceFileRepository = Objects.requireNonNull(evidenceFileRepository);
        this.fileStorage = Objects.requireNonNull(fileStorage);
        this.clock = Objects.requireNonNull(clock);
    }

    public Optional<EvidenceFile> delete(UUID evidenceId) {
        return evidenceFileRepository.findByEvidenceId(evidenceId)
                .map(this::delete);
    }

    private EvidenceFile delete(EvidenceFile evidenceFile) {
        EvidenceFile deletionPending = evidenceFile.requestDeletion(clock.instant());
        if (deletionPending.state() != evidenceFile.state()) {
            evidenceFileRepository.update(deletionPending);
        }
        if (deletionPending.state() == EvidenceFileState.DELETED) {
            return deletionPending;
        }
        return completeDeletion(deletionPending);
    }

    private EvidenceFile completeDeletion(EvidenceFile deletionPending) {
        fileStorage.delete(deletionPending.objectKey());
        EvidenceFile deleted = deletionPending.completeDeletion(clock.instant());
        evidenceFileRepository.update(deleted);
        return deleted;
    }

    public FileDeletionReconciliationResult reconcilePendingDeletions() {
        int attempted = 0;
        int deleted = 0;
        int failed = 0;

        for (EvidenceFile evidenceFile : evidenceFileRepository.findAllByState(
                EvidenceFileState.DELETION_PENDING
        )) {
            attempted++;
            try {
                completeDeletion(evidenceFile);
                deleted++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        return new FileDeletionReconciliationResult(attempted, deleted, failed);
    }
}
