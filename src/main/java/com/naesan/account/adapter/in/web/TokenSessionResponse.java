package com.naesan.account.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.security.TokenSession;

public record TokenSessionResponse(
        UUID accountId,
        String email,
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt
) {

    public static TokenSessionResponse from(TokenSession session) {
        return new TokenSessionResponse(
                session.account().id(),
                session.account().email(),
                "Bearer",
                session.accessToken().value(),
                session.accessToken().expiresAt()
        );
    }
}
