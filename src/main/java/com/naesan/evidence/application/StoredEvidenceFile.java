package com.naesan.evidence.application;

import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.evidence.domain.StorageKey;

public record StoredEvidenceFile(
        StorageKey key,
        EvidenceFileType fileType,
        long size,
        String sha256
) {
}
