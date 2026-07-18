package com.naesan.evidence.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidenceState;

public record EvidenceConfirmationResponse(
        UUID evidenceId,
        String state,
        UUID snapshotId,
        Instant confirmedAt
) {

    public static EvidenceConfirmationResponse from(EvidenceSnapshot snapshot) {
        return new EvidenceConfirmationResponse(
                snapshot.evidenceId(),
                PurchaseEvidenceState.CONFIRMED.name(),
                snapshot.id(),
                snapshot.createdAt()
        );
    }
}
