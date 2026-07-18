package com.naesan.evidence.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.naesan.evidence.application.EvidenceDetails;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;

public record EvidenceDetailsResponse(
        UUID id,
        String state,
        String merchantName,
        String productName,
        String serialNumber,
        LocalDate purchasedAt,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        EvidenceFileResponse file
) {

    public static EvidenceDetailsResponse from(EvidenceDetails details) {
        PurchaseEvidence evidence = details.evidence();
        EvidenceMetadata metadata = evidence.metadata();
        return new EvidenceDetailsResponse(
                evidence.id(),
                evidence.state().name(),
                metadata.merchantName(),
                metadata.productName(),
                metadata.serialNumber(),
                metadata.purchasedAt(),
                metadata.amount(),
                metadata.currency(),
                evidence.createdAt(),
                evidence.updatedAt(),
                evidence.confirmedAt(),
                details.file()
                        .map(EvidenceFileResponse::from)
                        .orElse(null)
        );
    }
}
