package com.naesan.evidence.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.naesan.evidence.domain.PurchaseEvidence;

public interface PurchaseEvidenceRepository {

    void save(PurchaseEvidence evidence);

    void update(PurchaseEvidence evidence);

    List<PurchaseEvidence> findAllByOwnerAccountId(UUID ownerAccountId);

    Optional<PurchaseEvidence> findById(UUID evidenceId);
}
