package com.naesan.evidence.application;

import java.util.Objects;
import java.util.UUID;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.PurchaseEvidence;

public class GetEvidenceDetailsService {
    private final PurchaseEvidenceRepository evidenceRepository;
    private final EvidenceFileRepository evidenceFileRepository;

    public GetEvidenceDetailsService(
            PurchaseEvidenceRepository evidenceRepository,
            EvidenceFileRepository evidenceFileRepository
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.evidenceFileRepository = Objects.requireNonNull(evidenceFileRepository);
    }

    public EvidenceDetails get(UUID ownerAccountId, UUID evidenceId) {
        PurchaseEvidence evidence = evidenceRepository.findById(evidenceId)
                .filter(foundEvidence ->
                        foundEvidence.ownerAccountId().equals(ownerAccountId))
                .orElseThrow(EvidenceException::notFound);
        return new EvidenceDetails(
                evidence,
                evidenceFileRepository.findByEvidenceId(evidenceId)
        );
    }
}
