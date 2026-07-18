package com.naesan.evidence.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.PurchaseEvidence;

public class ListEvidenceService {
    private final PurchaseEvidenceRepository evidenceRepository;

    public ListEvidenceService(PurchaseEvidenceRepository evidenceRepository) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
    }

    public List<PurchaseEvidence> list(UUID ownerAccountId) {
        return evidenceRepository.findAllByOwnerAccountId(ownerAccountId);
    }
}
