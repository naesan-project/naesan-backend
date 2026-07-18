package com.naesan.evidence.application;

import java.io.InputStream;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.PurchaseEvidenceState;
import com.naesan.evidence.domain.StorageKey;

public class AttachEvidenceFileService {
    private final PurchaseEvidenceRepository evidenceRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final StoreTemporaryEvidenceFileService storeFileService;
    private final FileStorage fileStorage;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AttachEvidenceFileService(
            PurchaseEvidenceRepository evidenceRepository,
            EvidenceFileRepository evidenceFileRepository,
            StoreTemporaryEvidenceFileService storeFileService,
            FileStorage fileStorage,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.evidenceFileRepository = Objects.requireNonNull(evidenceFileRepository);
        this.storeFileService = Objects.requireNonNull(storeFileService);
        this.fileStorage = Objects.requireNonNull(fileStorage);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    public EvidenceFile attach(
            UUID ownerAccountId,
            UUID evidenceId,
            InputStream content,
            String declaredMediaType
    ) {
        requireAttachable(ownerAccountId, evidenceId);
        StoredEvidenceFile storedFile = storeFileService.store(content, declaredMediaType);

        try {
            return transactionTemplate.execute(status -> persistAttachment(
                    ownerAccountId,
                    evidenceId,
                    storedFile
            ));
        } catch (RuntimeException exception) {
            deleteStoredObject(storedFile.key(), exception);
            throw exception;
        }
    }

    private PurchaseEvidence requireAttachable(UUID ownerAccountId, UUID evidenceId) {
        PurchaseEvidence evidence = evidenceRepository.findById(evidenceId)
                .filter(foundEvidence -> foundEvidence.ownerAccountId().equals(ownerAccountId))
                .orElseThrow(EvidenceException::notFound);
        if (evidence.state() != PurchaseEvidenceState.DRAFT) {
            throw EvidenceException.notEditable();
        }
        if (evidenceFileRepository.findByEvidenceId(evidenceId).isPresent()) {
            throw EvidenceException.fileAlreadyAttached();
        }
        return evidence;
    }

    private EvidenceFile persistAttachment(
            UUID ownerAccountId,
            UUID evidenceId,
            StoredEvidenceFile storedFile
    ) {
        PurchaseEvidence evidence = requireAttachable(ownerAccountId, evidenceId);
        EvidenceFile evidenceFile = EvidenceFile.createTemporary(
                UUID.randomUUID(),
                evidenceId,
                storedFile.key(),
                storedFile.sha256(),
                storedFile.fileType(),
                storedFile.size(),
                clock.instant()
        );
        evidenceFileRepository.save(evidenceFile);
        evidenceRepository.update(evidence.attachFile(clock.instant()));
        return evidenceFile;
    }

    private void deleteStoredObject(StorageKey objectKey, RuntimeException originalFailure) {
        try {
            fileStorage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
