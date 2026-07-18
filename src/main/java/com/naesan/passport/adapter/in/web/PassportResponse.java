package com.naesan.passport.adapter.in.web;

import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.naesan.passport.application.IssuedPassport;
import com.naesan.passport.domain.ProofAnchor;

public record PassportResponse(
        UUID id,
        UUID snapshotId,
        String status,
        ProofResponse proof,
        Instant createdAt
) {

    public static PassportResponse from(IssuedPassport issuedPassport) {
        return new PassportResponse(
                issuedPassport.passport().id(),
                issuedPassport.passport().snapshotId(),
                issuedPassport.passport().status().name(),
                ProofResponse.from(issuedPassport.proofAnchor()),
                issuedPassport.passport().createdAt()
        );
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
