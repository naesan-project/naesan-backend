package com.naesan.share.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.share.domain.PublicShare;

public record CurrentPublicShareResponse(
        UUID id,
        UUID passportId,
        String capability,
        Instant expiresAt,
        Instant createdAt
) {

    public static CurrentPublicShareResponse from(PublicShare publicShare) {
        return new CurrentPublicShareResponse(
                publicShare.id(),
                publicShare.passportId(),
                publicShare.capability().name(),
                publicShare.expiresAt(),
                publicShare.createdAt()
        );
    }
}
