package com.naesan.evidence.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.PurchaseEvidenceState;
import com.naesan.evidence.domain.StorageKey;

public class ConfirmEvidenceService {
    private final PurchaseEvidenceRepository evidenceRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceSnapshotRepository snapshotRepository;
    private final FileStorage fileStorage;
    private final EvidenceSnapshotCanonicalizer canonicalizer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ConfirmEvidenceService(
            PurchaseEvidenceRepository evidenceRepository,
            EvidenceFileRepository evidenceFileRepository,
            EvidenceSnapshotRepository snapshotRepository,
            FileStorage fileStorage,
            EvidenceSnapshotCanonicalizer canonicalizer,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.evidenceFileRepository = Objects.requireNonNull(evidenceFileRepository);
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository);
        this.fileStorage = Objects.requireNonNull(fileStorage);
        this.canonicalizer = Objects.requireNonNull(canonicalizer);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    public EvidenceSnapshot confirm(UUID ownerAccountId, UUID evidenceId) {
        PurchaseEvidence evidence = ownedEvidence(ownerAccountId, evidenceId);
        EvidenceSnapshot existingSnapshot = snapshotRepository.findByEvidenceId(evidenceId)
                .orElse(null);
        if (existingSnapshot != null) {
            return existingSnapshot;
        }
        requireConfirmable(evidence);

        EvidenceFile temporaryFile = evidenceFileRepository.findByEvidenceId(evidenceId)
                .filter(file -> file.state() == EvidenceFileState.TEMPORARY)
                .orElseThrow(EvidenceException::notEditable);
        StorageKey temporaryKey = temporaryFile.objectKey();
        StorageKey permanentKey = fileStorage.promote(temporaryKey);
        Instant confirmedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        EvidenceSnapshot snapshot = persistOrResolveConfirmation(
                ownerAccountId,
                evidenceId,
                permanentKey,
                confirmedAt
        );
        fileStorage.delete(temporaryKey);
        return snapshot;
    }

    private EvidenceSnapshot persistOrResolveConfirmation(
            UUID ownerAccountId,
            UUID evidenceId,
            StorageKey permanentKey,
            Instant confirmedAt
    ) {
        try {
            return transactionTemplate.execute(status ->
                    persistConfirmation(
                            ownerAccountId,
                            evidenceId,
                            permanentKey,
                            confirmedAt
                    )
            );
        } catch (RuntimeException confirmationFailure) {
            return resolveConcurrentConfirmation(
                    evidenceId,
                    permanentKey,
                    confirmationFailure
            );
        }
    }

    private EvidenceSnapshot resolveConcurrentConfirmation(
            UUID evidenceId,
            StorageKey redundantPermanentKey,
            RuntimeException confirmationFailure
    ) {
        deleteRedundantObject(redundantPermanentKey, confirmationFailure);
        return snapshotRepository.findByEvidenceId(evidenceId)
                .orElseThrow(() -> confirmationFailure);
    }

    private void deleteRedundantObject(
            StorageKey redundantPermanentKey,
            RuntimeException confirmationFailure
    ) {
        try {
            fileStorage.delete(redundantPermanentKey);
        } catch (RuntimeException cleanupFailure) {
            confirmationFailure.addSuppressed(cleanupFailure);
        }
    }

    private PurchaseEvidence ownedEvidence(UUID ownerAccountId, UUID evidenceId) {
        return evidenceRepository.findById(evidenceId)
                .filter(evidence -> evidence.ownerAccountId().equals(ownerAccountId))
                .orElseThrow(EvidenceException::notFound);
    }

    private void requireConfirmable(PurchaseEvidence evidence) {
        if (evidence.state() != PurchaseEvidenceState.FILE_ATTACHED) {
            throw EvidenceException.notEditable();
        }
    }

    private EvidenceSnapshot persistConfirmation(
            UUID ownerAccountId,
            UUID evidenceId,
            StorageKey permanentKey,
            Instant confirmedAt
    ) {
        PurchaseEvidence evidence = ownedEvidence(ownerAccountId, evidenceId);
        requireConfirmable(evidence);
        EvidenceFile temporaryFile = evidenceFileRepository.findByEvidenceId(evidenceId)
                .filter(file -> file.state() == EvidenceFileState.TEMPORARY)
                .orElseThrow(EvidenceException::notEditable);
        EvidenceFile promotedFile = temporaryFile.promote(permanentKey, confirmedAt);
        EvidenceSnapshot snapshot = canonicalizer.createSnapshot(
                evidence,
                promotedFile,
                confirmedAt
        );

        evidenceFileRepository.update(promotedFile);
        snapshotRepository.save(snapshot);
        evidenceRepository.update(evidence.confirm(confirmedAt));
        return snapshot;
    }
}
