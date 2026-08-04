package com.naesan.passport.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import com.naesan.passport.application.PassportDetails;
import com.naesan.passport.domain.ProofAnchor;

public record PassportDetailsResponse(
        UUID id,
        UUID snapshotId,
        String status,
        ProductResponse product,
        ProofResponse proof,
        Instant createdAt
) {

    public static PassportDetailsResponse from(PassportDetails details) {
        return new PassportDetailsResponse(
                details.passport().id(),
                details.passport().snapshotId(),
                details.passport().status().name(),
                new ProductResponse(
                        details.productName(),
                        details.merchantName(),
                        details.purchasedAt()
                ),
                ProofResponse.from(details.proofAnchor()),
                details.passport().createdAt()
        );
    }

    public record ProductResponse(
            String name,
            String merchantName,
            LocalDate purchasedAt
    ) {
    }

    public record ProofResponse(
            String state,
            String commitment
    ) {

        private static ProofResponse from(ProofAnchor proofAnchor) {
            return new ProofResponse(
                    proofAnchor.state().name(),
                    HexFormat.of().formatHex(proofAnchor.commitment())
            );
        }
    }
}
