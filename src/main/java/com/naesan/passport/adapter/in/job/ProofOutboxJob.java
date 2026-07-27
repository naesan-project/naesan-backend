package com.naesan.passport.adapter.in.job;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
    private static final String WORKER_RUN_ID_MDC_KEY = "worker_run_id";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProofOutboxJob.class);
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
        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                WORKER_RUN_ID_MDC_KEY,
                UUID.randomUUID().toString()
        )) {
            processBatch();
        }
    }

    private void processBatch() {
        int processedCount = 0;
        while (processedCount < batchSize
                && processProofOutboxService.processNext(workerId)) {
            processedCount++;
        }
        processProofOutboxService.refreshStatusMetrics();
        if (processedCount > 0) {
            LOGGER.atInfo()
                    .addKeyValue("event", "proof_outbox_batch_completed")
                    .addKeyValue("processed", processedCount)
                    .log("Proof outbox batch completed");
        }
    }
}
