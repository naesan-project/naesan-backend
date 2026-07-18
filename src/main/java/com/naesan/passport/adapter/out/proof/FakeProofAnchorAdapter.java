package com.naesan.passport.adapter.out.proof;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofAnchorReceipt;
import com.naesan.passport.application.port.out.ProofProviderCapabilities;

public class FakeProofAnchorAdapter implements ProofAnchorPort {
    private static final ProofProviderCapabilities CAPABILITIES =
            new ProofProviderCapabilities(true, true);
    private static final String EXTERNAL_REFERENCE_PREFIX = "fake:";

    private final Clock clock;
    private final Map<String, ProofAnchorReceipt> receiptsByCommitment =
            new ConcurrentHashMap<>();

    public FakeProofAnchorAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ProofProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProofAnchorReceipt submit(ProofAnchorCommand command) {
        return receiptsByCommitment.computeIfAbsent(
                command.commitment(),
                commitment -> new ProofAnchorReceipt(
                        EXTERNAL_REFERENCE_PREFIX + commitment,
                        clock.instant()
                )
        );
    }

    @Override
    public Optional<ProofAnchorReceipt> lookup(String commitment) {
        return Optional.ofNullable(receiptsByCommitment.get(commitment));
    }
}
