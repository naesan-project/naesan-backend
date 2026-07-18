package com.naesan.passport.application.port.out;

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

    boolean completeClaimed(OutboxEvent succeededEvent);
}
