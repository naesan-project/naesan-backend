package com.naesan.passport.application;

import java.util.HexFormat;
import java.util.Objects;
import java.time.Duration;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofAnchorReceipt;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OutboxClaim;
import com.naesan.passport.domain.ProofAnchor;

public class ProcessProofOutboxService {
    private final OutboxEventRepository outboxEventRepository;
    private final ProofAnchorRepository proofAnchorRepository;
    private final ProofAnchorPort proofAnchorPort;
    private final TransactionTemplate transactionTemplate;
    private final Duration leaseDuration;
    private final OutboxRetryPolicy retryPolicy;

    public ProcessProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            ProofAnchorPort proofAnchorPort,
            TransactionTemplate transactionTemplate,
            Duration leaseDuration,
            OutboxRetryPolicy retryPolicy
    ) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.proofAnchorRepository = Objects.requireNonNull(proofAnchorRepository);
        this.proofAnchorPort = Objects.requireNonNull(proofAnchorPort);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
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
        OutboxEvent claimedEvent = claim.event();

        ProofAnchor proofAnchor = proofAnchorRepository.findById(
                        claimedEvent.proofAnchorId()
                )
                .orElseThrow(() -> new OutboxProcessingException(
                        "Outbox event의 Proof anchor를 찾을 수 없습니다."
                ));
        ProofAnchorReceipt receipt;
        try {
            receipt = proofAnchorPort.submit(
                    new ProofAnchorCommand(
                            claimedEvent.dispatchKey(),
                            HexFormat.of().formatHex(proofAnchor.commitment())
                    )
            );
        } catch (ProofProviderException failure) {
            if (failure.failureType() == ProofFailureType.AMBIGUOUS) {
                throw failure;
            }
            transactionTemplate.executeWithoutResult(status ->
                    finalizeFailure(claim, failure)
            );
            return true;
        }

        transactionTemplate.executeWithoutResult(status ->
                finalizeSuccess(claim, proofAnchor, receipt)
        );
        return true;
    }

    private void finalizeFailure(
            OutboxClaim claim,
            ProofProviderException failure
    ) {
        boolean finalized;
        if (failure.failureType() == ProofFailureType.PERMANENT) {
            finalized = outboxEventRepository.moveToDeadLetter(claim, failure);
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
        }
        if (!finalized) {
            throw new OutboxProcessingException(
                    "외부 증명 실패 결과를 일관되게 저장하지 못했습니다."
            );
        }
    }

    private void finalizeSuccess(
            OutboxClaim claim,
            ProofAnchor proofAnchor,
            ProofAnchorReceipt receipt
    ) {
        ProofAnchor confirmedProof = proofAnchor
                .submit(receipt.externalReference(), receipt.anchoredAt())
                .confirm(receipt.anchoredAt());
        OutboxEvent succeededEvent = claim.event().succeed(receipt.anchoredAt());

        boolean proofConfirmed = proofAnchorRepository.confirmPrepared(confirmedProof);
        boolean eventCompleted = outboxEventRepository.completeClaimed(
                claim,
                succeededEvent
        );
        if (!proofConfirmed || !eventCompleted) {
            throw new OutboxProcessingException(
                    "외부 증명 처리 결과를 일관되게 저장하지 못했습니다."
            );
        }
    }
}
