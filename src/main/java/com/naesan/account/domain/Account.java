package com.naesan.account.domain;

import java.time.Instant;
import java.util.UUID;

public final class Account {
    private final UUID id;
    private final Email email;
    private final PasswordHash passwordHash;
    private final AccountStatus status;
    private final Instant createdAt;

    private Account(
            UUID id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Instant createdAt
    ) {
        validate(id, email, passwordHash, status, createdAt);
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.createdAt = createdAt;
    }

    private static void validate(
            UUID id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Instant createdAt
    ) {
        if (id == null
                || email == null
                || passwordHash == null
                || status == null
                || createdAt == null) {
            throw new IllegalArgumentException("계정의 필수 값은 null일 수 없습니다.");
        }
    }

    public static Account create(UUID id, Email email, PasswordHash passwordHash, Instant createdAt) {
        return new Account(id, email, passwordHash, AccountStatus.ACTIVE, createdAt);
    }

    public static Account restore(
            UUID id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Instant createdAt
    ) {
        return new Account(id, email, passwordHash, status, createdAt);
    }

    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Account account)) {
            return false;
        }

        return id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
