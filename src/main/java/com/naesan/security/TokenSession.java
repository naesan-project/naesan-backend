package com.naesan.security;

import java.time.Instant;
import java.util.Objects;

public record TokenSession(
        AuthenticatedAccount account,
        AccessToken accessToken,
        String rawRefreshToken,
        Instant refreshTokenExpiresAt
) {

    public TokenSession {
        Objects.requireNonNull(account);
        Objects.requireNonNull(accessToken);
        Objects.requireNonNull(rawRefreshToken);
        Objects.requireNonNull(refreshTokenExpiresAt);
    }
}
