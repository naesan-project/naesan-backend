package com.naesan.evidence.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;

public record EvidenceDraftResponse(
        UUID id,
        String state,
        String merchantName,
        String productName,
        String serialNumber,
        LocalDate purchasedAt,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {

    public static EvidenceDraftResponse from(PurchaseEvidence evidence) {
        EvidenceMetadata metadata = evidence.metadata();
        return new EvidenceDraftResponse(
                evidence.id(),
                evidence.state().name(),
                metadata.merchantName(),
                metadata.productName(),
                metadata.serialNumber(),
                metadata.purchasedAt(),
                metadata.amount(),
                metadata.currency(),
                evidence.createdAt()
        );
    }
}
