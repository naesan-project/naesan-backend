package com.naesan.evidence.adapter.in.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.naesan.evidence.application.OrphanReconciliationResult;
import com.naesan.evidence.application.ReconcileOrphanEvidenceFilesService;

@Component
public class EvidenceOrphanReconciliationJob {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EvidenceOrphanReconciliationJob.class);

    private final ReconcileOrphanEvidenceFilesService reconciliationService;

    public EvidenceOrphanReconciliationJob(
            ReconcileOrphanEvidenceFilesService reconciliationService
    ) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            initialDelayString = "${naesan.evidence.orphan.initial-delay}",
            fixedDelayString = "${naesan.evidence.orphan.interval}"
    )
    public void reconcile() {
        OrphanReconciliationResult result = reconciliationService.reconcile();
        if (result.deleted() > 0 || result.failed() > 0) {
            LOGGER.info(
                    "Evidence orphan reconciliation completed: scanned={}, deleted={}, failed={}",
                    result.scanned(),
                    result.deleted(),
                    result.failed()
            );
        }
    }
}
