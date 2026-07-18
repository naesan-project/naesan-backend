package com.naesan.evidence.application;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceFile;

public class DownloadEvidenceFileService {
    private final PurchaseEvidenceRepository evidenceRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final FileStorage fileStorage;

    public DownloadEvidenceFileService(
            PurchaseEvidenceRepository evidenceRepository,
            EvidenceFileRepository evidenceFileRepository,
            FileStorage fileStorage
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.evidenceFileRepository = Objects.requireNonNull(evidenceFileRepository);
        this.fileStorage = Objects.requireNonNull(fileStorage);
    }

    public DownloadedEvidenceFile download(
            UUID ownerAccountId,
            UUID evidenceId
    ) {
        requireOwnership(ownerAccountId, evidenceId);
        EvidenceFile evidenceFile = evidenceFileRepository.findByEvidenceId(evidenceId)
                .filter(EvidenceFile::isDownloadable)
                .orElseThrow(EvidenceException::notFound);
        return new DownloadedEvidenceFile(
                evidenceFile,
                open(evidenceFile)
        );
    }

    private void requireOwnership(UUID ownerAccountId, UUID evidenceId) {
        evidenceRepository.findById(evidenceId)
                .filter(evidence -> evidence.ownerAccountId().equals(ownerAccountId))
                .orElseThrow(EvidenceException::notFound);
    }

    private InputStream open(EvidenceFile evidenceFile) {
        try {
            return fileStorage.open(evidenceFile.objectKey());
        } catch (FileStorageException exception) {
            throw EvidenceException.fileUnavailable();
        }
    }
}
