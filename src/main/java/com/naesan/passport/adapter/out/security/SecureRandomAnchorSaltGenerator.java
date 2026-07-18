package com.naesan.passport.adapter.out.security;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.naesan.passport.application.port.out.AnchorSaltGenerator;

@Component
public class SecureRandomAnchorSaltGenerator implements AnchorSaltGenerator {
    private static final int ANCHOR_SALT_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom;

    public SecureRandomAnchorSaltGenerator() {
        this(new SecureRandom());
    }

    SecureRandomAnchorSaltGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public byte[] generate() {
        byte[] anchorSalt = new byte[ANCHOR_SALT_BYTE_LENGTH];
        secureRandom.nextBytes(anchorSalt);
        return anchorSalt;
    }
}
