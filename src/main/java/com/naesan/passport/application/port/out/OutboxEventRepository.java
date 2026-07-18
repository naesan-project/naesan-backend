package com.naesan.passport.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.passport.domain.OutboxEvent;

public interface OutboxEventRepository {

    void save(OutboxEvent outboxEvent);

    Optional<OutboxEvent> findById(UUID outboxEventId);

    Optional<OutboxEvent> findByProofAnchorId(UUID proofAnchorId);
}
