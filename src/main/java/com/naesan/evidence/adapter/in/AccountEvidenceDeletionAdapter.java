package com.naesan.evidence.adapter.in;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.naesan.account.application.port.out.AccountEvidenceDeletion;
import com.naesan.account.application.port.out.AccountEvidenceDeletionResult;
import com.naesan.evidence.application.DeleteEvidenceFileService;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.PurchaseEvidence;

public class AccountEvidenceDeletionAdapter implements AccountEvidenceDeletion {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(AccountEvidenceDeletionAdapter.class);

    private final PurchaseEvidenceRepository evidenceRepository;
    private final DeleteEvidenceFileService deleteEvidenceFileService;

    public AccountEvidenceDeletionAdapter(
            PurchaseEvidenceRepository evidenceRepository,
            DeleteEvidenceFileService deleteEvidenceFileService
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.deleteEvidenceFileService =
                Objects.requireNonNull(deleteEvidenceFileService);
    }

    @Override
    public AccountEvidenceDeletionResult deleteAll(UUID accountId) {
        int evidenceCount = 0;
        int deletedFileCount = 0;
        int pendingFileCount = 0;

        for (PurchaseEvidence evidence :
                evidenceRepository.findAllByOwnerAccountId(accountId)) {
            evidenceCount++;
            try {
                if (deleteEvidenceFileService.delete(evidence.id()).isPresent()) {
                    deletedFileCount++;
                }
            } catch (RuntimeException exception) {
                pendingFileCount++;
            }
        }
        if (pendingFileCount > 0) {
            LOGGER.warn(
                    "Account Evidence file deletion deferred: evidence={}, pending={}",
                    evidenceCount,
                    pendingFileCount
            );
        }
        return new AccountEvidenceDeletionResult(
                evidenceCount,
                deletedFileCount,
                pendingFileCount
        );
    }
}
