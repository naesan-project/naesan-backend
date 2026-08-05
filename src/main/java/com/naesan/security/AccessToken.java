package com.naesan.security;

import java.time.Instant;
import java.util.Objects;

public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value);
        Objects.requireNonNull(expiresAt);
    }
}
