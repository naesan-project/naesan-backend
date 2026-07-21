package com.naesan.share.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import com.naesan.share.application.port.out.GeneratedPublicShareToken;
import com.naesan.share.application.port.out.PublicShareTokenCodec;

public final class SecurePublicShareTokenCodec implements PublicShareTokenCodec {
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int RAW_TOKEN_LENGTH = 43;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom;

    public SecurePublicShareTokenCodec() {
        this(new SecureRandom());
    }

    SecurePublicShareTokenCodec(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedPublicShareToken generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
        return new GeneratedPublicShareToken(rawToken, sha256(rawToken));
    }

    @Override
    public Optional<byte[]> hash(String rawToken) {
        if (!isWellFormed(rawToken)) {
            return Optional.empty();
        }
        return Optional.of(sha256(rawToken));
    }

    private boolean isWellFormed(String rawToken) {
        return rawToken != null
                && rawToken.length() == RAW_TOKEN_LENGTH
                && rawToken.chars().allMatch(this::isUrlSafeBase64Character);
    }

    private boolean isUrlSafeBase64Character(int character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '-'
                || character == '_';
    }

    private byte[] sha256(String rawToken) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(rawToken.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
