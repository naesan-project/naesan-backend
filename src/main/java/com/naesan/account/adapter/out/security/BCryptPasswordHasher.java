package com.naesan.account.adapter.out.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.PasswordHash;

@Component
public final class BCryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash.value());
    }
}
