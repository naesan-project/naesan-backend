package com.naesan.share.application.port.out;

import java.util.Objects;

public final class GeneratedPublicShareToken {
    private final String rawToken;
    private final byte[] tokenHash;

    public GeneratedPublicShareToken(String rawToken, byte[] tokenHash) {
        this.rawToken = Objects.requireNonNull(rawToken);
        this.tokenHash = Objects.requireNonNull(tokenHash).clone();
    }

    public String rawToken() {
        return rawToken;
    }

    public byte[] tokenHash() {
        return tokenHash.clone();
    }
}
