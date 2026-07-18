package com.naesan.passport.adapter.in.web;

import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.naesan.passport.application.IssuedPassport;
import com.naesan.passport.application.PassportDetails;
import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.ProofAnchor;

public record PassportResponse(
        UUID id,
        UUID snapshotId,
        String status,
        ProofResponse proof,
        Instant createdAt
) {

    public static PassportResponse from(IssuedPassport issuedPassport) {
        return of(issuedPassport.passport(), issuedPassport.proofAnchor());
    }

    public static PassportResponse from(PassportDetails details) {
        return of(details.passport(), details.proofAnchor());
    }

    private static PassportResponse of(
            Passport passport,
            ProofAnchor proofAnchor
    ) {
        return new PassportResponse(
                passport.id(),
                passport.snapshotId(),
                passport.status().name(),
                ProofResponse.from(proofAnchor),
                passport.createdAt()
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
