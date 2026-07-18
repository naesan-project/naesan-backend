package com.naesan.evidence.adapter.in.web;

import java.util.UUID;

import com.naesan.evidence.domain.EvidenceFile;

public record EvidenceFileResponse(
        UUID id,
        String state,
        String mediaType,
        long size
) {

    public static EvidenceFileResponse from(EvidenceFile evidenceFile) {
        return new EvidenceFileResponse(
                evidenceFile.id(),
                evidenceFile.state().name(),
                evidenceFile.fileType().mediaType(),
                evidenceFile.size()
        );
    }
}
