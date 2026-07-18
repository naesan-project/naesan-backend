package com.naesan.passport.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.naesan.passport.application.OutboxClaimRequest;
import com.naesan.passport.domain.OutboxClaim;
import com.naesan.passport.domain.OutboxEvent;

public interface OutboxEventRepository {

    void save(OutboxEvent outboxEvent);

    Optional<OutboxEvent> findById(UUID outboxEventId);

    Optional<OutboxEvent> findByProofAnchorId(UUID proofAnchorId);

    Optional<OutboxClaim> claimNextDue(OutboxClaimRequest request);

    boolean completeClaimed(
            OutboxClaim claim,
            OutboxEvent succeededEvent
    );

    boolean scheduleRetry(
            OutboxClaim claim,
            Duration delay,
            ProofProviderException failure
    );

    boolean moveToDeadLetter(
            OutboxClaim claim,
            ProofProviderException failure
    );
}
