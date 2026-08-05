package com.naesan.passport.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofAnchorReceipt;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.application.port.out.ProofOutboxTelemetry;
import com.naesan.passport.domain.OutboxClaim;
import com.naesan.passport.domain.OutboxClaimReason;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OutboxEventStatus;
import com.naesan.passport.domain.ProofAnchor;

public class ProcessProofOutboxService {
    private static final String LOOKUP_UNSUPPORTED = "LOOKUP_UNSUPPORTED";
    private static final String ANCHOR_NOT_FOUND = "ANCHOR_NOT_FOUND";
    private static final String SUBMISSION_NOT_FINALIZED = "SUBMISSION_NOT_FINALIZED";

    private final OutboxEventRepository outboxEventRepository;
    private final ProofAnchorRepository proofAnchorRepository;
    private final ProofAnchorPort proofAnchorPort;
    private final TransactionTemplate transactionTemplate;
    private final Duration leaseDuration;
    private final OutboxRetryPolicy retryPolicy;
    private final Clock clock;
    private final ProofOutboxTelemetry telemetry;

    public ProcessProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            ProofAnchorPort proofAnchorPort,
            TransactionTemplate transactionTemplate,
            Duration leaseDuration,
            OutboxRetryPolicy retryPolicy,
            Clock clock,
            ProofOutboxTelemetry telemetry
    ) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.proofAnchorRepository = Objects.requireNonNull(proofAnchorRepository);
        this.proofAnchorPort = Objects.requireNonNull(proofAnchorPort);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.clock = Objects.requireNonNull(clock);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    public boolean processNext(String workerId) {
        OutboxClaim claim = transactionTemplate.execute(status ->
                outboxEventRepository.claimNextDue(
                        new OutboxClaimRequest(
                                workerId,
                                UUID.randomUUID(),
                                leaseDuration
                        )
                ).orElse(null)
        );
        if (claim == null) {
            return false;
        }
        long processingStartedAt = System.nanoTime();
        OutboxEvent claimedEvent = claim.event();

        ProofAnchor proofAnchor = proofAnchorRepository.findById(
                        claimedEvent.proofAnchorId()
                )
                .orElseThrow(() -> new OutboxProcessingException(
                        "Outbox event의 Proof anchor를 찾을 수 없습니다."
                ));
        String commitment = HexFormat.of().formatHex(proofAnchor.commitment());
        if (claim.reason() == OutboxClaimReason.RECONCILIATION) {
            OutboxEventStatus status = reconcile(claim, proofAnchor, commitment);
            recordProcessed(claim, status, processingStartedAt);
            return true;
        }
        ProofAnchorReceipt receipt;
        try {
            receipt = proofAnchorPort.submit(
                    new ProofAnchorCommand(
                            claimedEvent.dispatchKey(),
                            commitment
                    )
            );
        } catch (ProofProviderException failure) {
            if (failure.failureType() == ProofFailureType.AMBIGUOUS) {
                transactionTemplate.executeWithoutResult(status ->
                        finalizeAmbiguousFailure(claim, proofAnchor, failure)
                );
                recordProcessed(
                        claim,
                        OutboxEventStatus.RECONCILE_PENDING,
                        processingStartedAt
                );
                return true;
            }
            OutboxEventStatus status = transactionTemplate.execute(transactionStatus ->
                    finalizeFailure(claim, failure)
            );
            recordProcessed(claim, status, processingStartedAt);
            return true;
        }

        OutboxEventStatus status = finalizeSubmitResult(
                claim,
                proofAnchor,
                receipt
        );
        recordProcessed(claim, status, processingStartedAt);
        return true;
    }

    private OutboxEventStatus finalizeSubmitResult(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofAnchorReceipt receipt
    ) {
        if (receipt.confirmed()) {
            transactionTemplate.executeWithoutResult(status ->
                    finalizeSuccess(claim, proofAnchor, receipt)
            );
            return OutboxEventStatus.SUCCEEDED;
        }
        return transactionTemplate.execute(status ->
                finalizeAwaitingSubmissionReconcile(claim, proofAnchor, receipt)
        );
    }

    private void recordProcessed(
            OutboxClaim claim,
            OutboxEventStatus status,
            long processingStartedAt
    ) {
        telemetry.recordProcessed(
                status,
                claim.event().attemptCount(),
                Duration.ofNanos(System.nanoTime() - processingStartedAt)
        );
    }

    public void refreshStatusMetrics() {
        var counts = outboxEventRepository.countByStatus();
        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            telemetry.updateStatusCount(status, counts.getOrDefault(status, 0L));
        }
    }

    private OutboxEventStatus reconcile(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            String commitment
    ) {
        if (!proofAnchorPort.capabilities().lookupSupported()) {
            ProofProviderException failure = new ProofProviderException(
                    ProofFailureType.AMBIGUOUS,
                    LOOKUP_UNSUPPORTED
            );
            transactionTemplate.executeWithoutResult(status ->
                    finalizeManualReview(claim, proofAnchor, failure)
            );
            return OutboxEventStatus.MANUAL_REVIEW;
        }

        Optional<ProofAnchorReceipt> receipt;
        try {
            receipt = proofAnchorPort.lookup(commitment);
        } catch (ProofProviderException failure) {
            transactionTemplate.executeWithoutResult(status ->
                    finalizeManualReview(claim, proofAnchor, failure)
            );
            return OutboxEventStatus.MANUAL_REVIEW;
        }

        if (receipt.isPresent() && receipt.orElseThrow().confirmed()) {
            transactionTemplate.executeWithoutResult(status ->
                    finalizeReconciledSuccess(
                            claim,
                            proofAnchor,
                            receipt.orElseThrow()
                    )
            );
            return OutboxEventStatus.SUCCEEDED;
        }
        if (receipt.isPresent()) {
            return transactionTemplate.execute(status ->
                    finalizePendingReconciliation(claim)
            );
        }
        return transactionTemplate.execute(status ->
                finalizeMissingAnchor(claim, proofAnchor)
        );
    }

    private void finalizeReconciledSuccess(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofAnchorReceipt receipt
    ) {
        Instant confirmedAt = clock.instant();
        ProofAnchor confirmedProof = proofAnchor.confirmReconciled(
                receipt.externalReference(),
                confirmedAt,
                receipt.evidence()
        );
        OutboxEvent succeededEvent = claim.event().succeed(confirmedAt);
        boolean proofConfirmed = proofAnchorRepository.confirmReconciled(
                confirmedProof
        );
        boolean eventCompleted = outboxEventRepository.completeClaimed(
                claim,
                succeededEvent
        );
        if (!proofConfirmed || !eventCompleted) {
            throw finalizeRejected(
                    "외부 증명 대사 결과를 일관되게 저장하지 못했습니다."
            );
        }
    }

    private OutboxEventStatus finalizeMissingAnchor(
            OutboxClaim claim,
            ProofAnchor proofAnchor
    ) {
        ProofProviderException failure = new ProofProviderException(
                ProofFailureType.RETRYABLE,
                ANCHOR_NOT_FOUND
        );
        ProofAnchor preparedProof = proofAnchor.resumeSubmission(clock.instant());
        OutboxRetryDecision decision = retryPolicy.decide(
                claim.event().attemptCount()
        );
        boolean proofPrepared = proofAnchorRepository.resumePrepared(preparedProof);
        boolean eventUpdated = decision.retryAllowed()
                ? outboxEventRepository.scheduleRetry(
                        claim,
                        decision.delay(),
                        failure
                )
                : outboxEventRepository.moveToDeadLetter(claim, failure);
        if (!proofPrepared || !eventUpdated) {
            throw finalizeRejected(
                    "외부 증명 재제출 상태를 일관되게 저장하지 못했습니다."
            );
        }
        return decision.retryAllowed()
                ? OutboxEventStatus.RETRY_WAIT
                : OutboxEventStatus.DEAD_LETTER;
    }

    private OutboxEventStatus finalizePendingReconciliation(OutboxClaim claim) {
        boolean eventUpdated = outboxEventRepository.scheduleReconciliation(
                claim,
                new ProofProviderException(
                        ProofFailureType.AMBIGUOUS,
                        SUBMISSION_NOT_FINALIZED
                )
        );
        if (!eventUpdated) {
            throw finalizeRejected("외부 증명 확정 대기를 저장하지 못했습니다.");
        }
        return OutboxEventStatus.RECONCILE_PENDING;
    }

    private void finalizeManualReview(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofProviderException failure
    ) {
        ProofAnchor manualReview = proofAnchor.requireManualReview(clock.instant());
        boolean proofUpdated = proofAnchorRepository.markManualReview(manualReview);
        boolean eventUpdated = outboxEventRepository.moveToManualReview(
                claim,
                failure
        );
        if (!proofUpdated || !eventUpdated) {
            throw finalizeRejected(
                    "외부 증명 수동 검토 상태를 일관되게 저장하지 못했습니다."
            );
        }
    }

    private void finalizeAmbiguousFailure(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofProviderException failure
    ) {
        ProofAnchor reconcilePending = proofAnchor.awaitReconciliation(
                clock.instant()
        );
        boolean proofUpdated = proofAnchorRepository.markReconcilePending(
                reconcilePending
        );
        boolean eventUpdated = outboxEventRepository.scheduleReconciliation(
                claim,
                failure
        );
        if (!proofUpdated || !eventUpdated) {
            throw finalizeRejected(
                    "외부 증명 대사 상태를 일관되게 저장하지 못했습니다."
            );
        }
    }

    private OutboxEventStatus finalizeFailure(
            OutboxClaim claim,
            ProofProviderException failure
    ) {
        boolean finalized;
        OutboxEventStatus finalStatus;
        if (failure.failureType() == ProofFailureType.PERMANENT) {
            finalized = outboxEventRepository.moveToDeadLetter(claim, failure);
            finalStatus = OutboxEventStatus.DEAD_LETTER;
        } else {
            OutboxRetryDecision decision = retryPolicy.decide(
                    claim.event().attemptCount()
            );
            finalized = decision.retryAllowed()
                    ? outboxEventRepository.scheduleRetry(
                            claim,
                            decision.delay(),
                            failure
                    )
                    : outboxEventRepository.moveToDeadLetter(claim, failure);
            finalStatus = decision.retryAllowed()
                    ? OutboxEventStatus.RETRY_WAIT
                    : OutboxEventStatus.DEAD_LETTER;
        }
        if (!finalized) {
            throw finalizeRejected(
                    "외부 증명 실패 결과를 일관되게 저장하지 못했습니다."
            );
        }
        return finalStatus;
    }

    private void finalizeSuccess(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofAnchorReceipt receipt
    ) {
        Instant confirmedAt = clock.instant();
        ProofAnchor confirmedProof = proofAnchor
                .submit(receipt.externalReference(), confirmedAt)
                .confirm(confirmedAt, receipt.evidence());
        OutboxEvent succeededEvent = claim.event().succeed(confirmedAt);

        boolean proofConfirmed = proofAnchorRepository.confirmPrepared(confirmedProof);
        boolean eventCompleted = outboxEventRepository.completeClaimed(
                claim,
                succeededEvent
        );
        if (!proofConfirmed || !eventCompleted) {
            throw finalizeRejected(
                    "외부 증명 처리 결과를 일관되게 저장하지 못했습니다."
            );
        }
    }

    private OutboxEventStatus finalizeAwaitingSubmissionReconcile(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofAnchorReceipt receipt
    ) {
        Instant changedAt = clock.instant();
        ProofAnchor reconciliableProof = proofAnchor.submit(
                receipt.externalReference(),
                changedAt
        ).awaitReconciliation(
                receipt.externalReference(),
                changedAt
        );

        boolean proofUpdated = proofAnchorRepository.markReconcilePending(
                reconciliableProof
        );
        boolean eventUpdated = outboxEventRepository.scheduleReconciliation(
                claim,
                new ProofProviderException(
                        ProofFailureType.AMBIGUOUS,
                        SUBMISSION_NOT_FINALIZED
                )
        );
        if (!proofUpdated || !eventUpdated) {
            throw finalizeRejected(
                    "외부 증명 대사 상태를 일관되게 저장하지 못했습니다."
            );
        }
        return OutboxEventStatus.RECONCILE_PENDING;
    }

    private OutboxProcessingException finalizeRejected(String message) {
        telemetry.recordFinalizeRejected();
        return new OutboxProcessingException(message);
    }
}
