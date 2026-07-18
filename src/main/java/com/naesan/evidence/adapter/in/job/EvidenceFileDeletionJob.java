package com.naesan.evidence.adapter.in.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.naesan.evidence.application.DeleteEvidenceFileService;
import com.naesan.evidence.application.FileDeletionReconciliationResult;

@Component
public class EvidenceFileDeletionJob {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EvidenceFileDeletionJob.class);

    private final DeleteEvidenceFileService deleteEvidenceFileService;

    public EvidenceFileDeletionJob(
            DeleteEvidenceFileService deleteEvidenceFileService
    ) {
        this.deleteEvidenceFileService = deleteEvidenceFileService;
    }

    @Scheduled(
            initialDelayString = "${naesan.evidence.deletion.initial-delay}",
            fixedDelayString = "${naesan.evidence.deletion.interval}"
    )
    public void deletePendingFiles() {
        FileDeletionReconciliationResult result =
                deleteEvidenceFileService.reconcilePendingDeletions();
        if (result.attempted() > 0) {
            LOGGER.info(
                    "Evidence file deletion completed: attempted={}, deleted={}, failed={}",
                    result.attempted(),
                    result.deleted(),
                    result.failed()
            );
        }
    }
}
