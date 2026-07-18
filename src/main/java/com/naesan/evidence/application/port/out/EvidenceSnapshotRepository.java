package com.naesan.evidence.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.evidence.domain.EvidenceSnapshot;

public interface EvidenceSnapshotRepository {

    void save(EvidenceSnapshot snapshot);

    Optional<EvidenceSnapshot> findByEvidenceId(UUID evidenceId);
}
