package com.naesan.evidence.application.port.out;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.StorageKey;

public interface EvidenceFileRepository {

    void save(EvidenceFile evidenceFile);

    void update(EvidenceFile evidenceFile);

    Set<StorageKey> findAllObjectKeys();

    List<EvidenceFile> findAllByState(EvidenceFileState state);

    Optional<EvidenceFile> findByEvidenceId(UUID evidenceId);
}
