package com.naesan.share.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.share.application.IssuedPublicShare;

public record IssuedPublicShareResponse(
        UUID id,
        UUID passportId,
        String capability,
        String rawToken,
        Instant expiresAt,
        Instant createdAt
) {

    public static IssuedPublicShareResponse from(IssuedPublicShare issuedShare) {
        return new IssuedPublicShareResponse(
                issuedShare.publicShare().id(),
                issuedShare.publicShare().passportId(),
                issuedShare.publicShare().capability().name(),
                issuedShare.rawToken(),
                issuedShare.publicShare().expiresAt(),
                issuedShare.publicShare().createdAt()
        );
    }
}
