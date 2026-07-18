package com.naesan.passport.support;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofAnchorReceipt;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderCapabilities;
import com.naesan.passport.application.port.out.ProofProviderException;

public final class ControllableProofAnchorAdapter implements ProofAnchorPort {
    private final Clock clock;
    private final Map<String, ProofAnchorReceipt> receiptsByCommitment =
            new ConcurrentHashMap<>();
    private final AtomicInteger submitCount = new AtomicInteger();
    private volatile ProofOutcome outcome = ProofOutcome.SUCCESS;
    private volatile boolean transactionActiveDuringSubmit;

    public ControllableProofAnchorAdapter(Clock clock) {
        this.clock = clock;
    }

    public void setOutcome(ProofOutcome outcome) {
        this.outcome = outcome;
    }

    public void reset() {
        receiptsByCommitment.clear();
        submitCount.set(0);
        outcome = ProofOutcome.SUCCESS;
        transactionActiveDuringSubmit = false;
    }

    public int submitCount() {
        return submitCount.get();
    }

    public boolean transactionActiveDuringSubmit() {
        return transactionActiveDuringSubmit;
    }

    @Override
    public ProofProviderCapabilities capabilities() {
        return new ProofProviderCapabilities(true, true);
    }

    @Override
    public ProofAnchorReceipt submit(ProofAnchorCommand command) {
        transactionActiveDuringSubmit =
                TransactionSynchronizationManager.isActualTransactionActive();
        submitCount.incrementAndGet();
        return switch (outcome) {
            case SUCCESS -> storeReceipt(command.commitment());
            case RETRYABLE_FAILURE -> throw new ProofProviderException(
                    ProofFailureType.RETRYABLE,
                    "PROVIDER_UNAVAILABLE"
            );
            case PERMANENT_FAILURE -> throw new ProofProviderException(
                    ProofFailureType.PERMANENT,
                    "INVALID_COMMAND"
            );
            case SUCCESS_THEN_RESPONSE_LOSS -> {
                storeReceipt(command.commitment());
                throw new ProofProviderException(
                        ProofFailureType.AMBIGUOUS,
                        "RESPONSE_LOST"
                );
            }
        };
    }

    private ProofAnchorReceipt storeReceipt(String commitment) {
        return receiptsByCommitment.computeIfAbsent(
                commitment,
                value -> new ProofAnchorReceipt(
                        "controlled:" + value,
                        clock.instant()
                )
        );
    }

    @Override
    public Optional<ProofAnchorReceipt> lookup(String commitment) {
        return Optional.ofNullable(receiptsByCommitment.get(commitment));
    }

    public enum ProofOutcome {
        SUCCESS,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE,
        SUCCESS_THEN_RESPONSE_LOSS
    }
}
