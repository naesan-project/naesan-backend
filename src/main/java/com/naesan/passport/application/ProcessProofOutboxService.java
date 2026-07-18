package com.naesan.passport.application;

import java.util.HexFormat;
import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofAnchorReceipt;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.ProofAnchor;

public class ProcessProofOutboxService {
    private final OutboxEventRepository outboxEventRepository;
    private final ProofAnchorRepository proofAnchorRepository;
    private final ProofAnchorPort proofAnchorPort;
    private final TransactionTemplate transactionTemplate;

    public ProcessProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            ProofAnchorPort proofAnchorPort,
            TransactionTemplate transactionTemplate
    ) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.proofAnchorRepository = Objects.requireNonNull(proofAnchorRepository);
        this.proofAnchorPort = Objects.requireNonNull(proofAnchorPort);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    }

    public boolean processNext(String workerId) {
        OutboxEvent claimedEvent = transactionTemplate.execute(status ->
                outboxEventRepository.claimNextPending(workerId).orElse(null)
        );
        if (claimedEvent == null) {
            return false;
        }

        ProofAnchor proofAnchor = proofAnchorRepository.findById(
                        claimedEvent.proofAnchorId()
                )
                .orElseThrow(() -> new OutboxProcessingException(
                        "Outbox event의 Proof anchor를 찾을 수 없습니다."
                ));
        ProofAnchorReceipt receipt = proofAnchorPort.submit(
                new ProofAnchorCommand(
                        claimedEvent.dispatchKey(),
                        HexFormat.of().formatHex(proofAnchor.commitment())
                )
        );

        transactionTemplate.executeWithoutResult(status ->
                finalizeSuccess(claimedEvent, proofAnchor, receipt)
        );
        return true;
    }

    private void finalizeSuccess(
            OutboxEvent claimedEvent,
            ProofAnchor proofAnchor,
            ProofAnchorReceipt receipt
    ) {
        ProofAnchor confirmedProof = proofAnchor
                .submit(receipt.externalReference(), receipt.anchoredAt())
                .confirm(receipt.anchoredAt());
        OutboxEvent succeededEvent = claimedEvent.succeed(receipt.anchoredAt());

        boolean proofConfirmed = proofAnchorRepository.confirmPrepared(confirmedProof);
        boolean eventCompleted = outboxEventRepository.completeClaimed(succeededEvent);
        if (!proofConfirmed || !eventCompleted) {
            throw new OutboxProcessingException(
                    "외부 증명 처리 결과를 일관되게 저장하지 못했습니다."
            );
        }
    }
}
