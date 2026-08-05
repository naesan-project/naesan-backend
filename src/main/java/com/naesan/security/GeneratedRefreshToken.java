package com.naesan.security;

import java.util.Objects;

public record GeneratedRefreshToken(String rawToken, byte[] tokenHash) {

    public GeneratedRefreshToken {
        Objects.requireNonNull(rawToken);
        tokenHash = Objects.requireNonNull(tokenHash).clone();
    }

    @Override
    public byte[] tokenHash() {
        return tokenHash.clone();
    }
}
