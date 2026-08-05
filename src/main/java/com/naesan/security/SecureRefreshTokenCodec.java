package com.naesan.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SecureRefreshTokenCodec implements RefreshTokenCodec {
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int TOKEN_CHARACTER_LENGTH = 43;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom secureRandom;

    public SecureRefreshTokenCodec(SecureRandom secureRandom) {
        this.secureRandom = java.util.Objects.requireNonNull(secureRandom);
    }

    @Override
    public GeneratedRefreshToken generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
        return new GeneratedRefreshToken(rawToken, digest(rawToken));
    }

    @Override
    public Optional<byte[]> hash(String rawToken) {
        if (rawToken == null
                || rawToken.length() != TOKEN_CHARACTER_LENGTH
                || !TOKEN_PATTERN.matcher(rawToken).matches()) {
            return Optional.empty();
        }
        return Optional.of(digest(rawToken));
    }

    private byte[] digest(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
