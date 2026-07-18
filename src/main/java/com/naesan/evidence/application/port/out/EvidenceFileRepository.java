package com.naesan.evidence.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.evidence.domain.EvidenceFile;

public interface EvidenceFileRepository {

    void save(EvidenceFile evidenceFile);

    void update(EvidenceFile evidenceFile);

    Optional<EvidenceFile> findByEvidenceId(UUID evidenceId);
}
