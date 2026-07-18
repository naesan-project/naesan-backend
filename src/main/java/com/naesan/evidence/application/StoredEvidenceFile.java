package com.naesan.evidence.application;

import com.naesan.evidence.application.port.out.StorageKey;
import com.naesan.evidence.domain.EvidenceFileType;

public record StoredEvidenceFile(
        StorageKey key,
        EvidenceFileType fileType,
        long size,
        String sha256
) {
}
