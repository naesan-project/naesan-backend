package com.naesan.evidence.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.evidence.domain.PurchaseEvidence;

public interface PurchaseEvidenceRepository {

    void save(PurchaseEvidence evidence);

    void update(PurchaseEvidence evidence);

    Optional<PurchaseEvidence> findById(UUID evidenceId);
}
