package com.naesan.passport.adapter.in.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.naesan.passport.application.ProcessProofOutboxService;

@Component
@ConditionalOnProperty(
        name = "naesan.proof.worker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProofOutboxJob {
    private final ProcessProofOutboxService processProofOutboxService;
    private final String workerId;
    private final int batchSize;

    public ProofOutboxJob(
            ProcessProofOutboxService processProofOutboxService,
            @Value("${naesan.proof.worker.id}") String workerId,
            @Value("${naesan.proof.worker.batch-size}") int batchSize
    ) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("Proof worker ID는 비어 있을 수 없습니다.");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Proof worker batch size는 0보다 커야 합니다.");
        }
        this.processProofOutboxService = processProofOutboxService;
        this.workerId = workerId;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${naesan.proof.worker.initial-delay}",
            fixedDelayString = "${naesan.proof.worker.interval}"
    )
    public void processPendingEvents() {
        int processedCount = 0;
        while (processedCount < batchSize
                && processProofOutboxService.processNext(workerId)) {
            processedCount++;
        }
        processProofOutboxService.refreshStatusMetrics();
    }
}
