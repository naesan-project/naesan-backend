package com.naesan.security;

import java.time.Instant;
import java.util.UUID;

public final class RefreshToken {
    private static final int TOKEN_HASH_BYTE_LENGTH = 32;

    private final UUID id;
    private final UUID accountId;
    private final byte[] tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant consumedAt;
    private final Instant revokedAt;

    private RefreshToken(
            UUID id,
            UUID accountId,
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant consumedAt,
            Instant revokedAt
    ) {
        validate(id, accountId, tokenHash, issuedAt, expiresAt, consumedAt, revokedAt);
        this.id = id;
        this.accountId = accountId;
        this.tokenHash = tokenHash.clone();
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.revokedAt = revokedAt;
    }

    private static void validate(
            UUID id,
            UUID accountId,
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant consumedAt,
            Instant revokedAt
    ) {
        if (id == null
                || accountId == null
                || tokenHash == null
                || issuedAt == null
                || expiresAt == null) {
            throw new IllegalArgumentException("Refresh token의 필수 값은 null일 수 없습니다.");
        }
        if (tokenHash.length != TOKEN_HASH_BYTE_LENGTH) {
            throw new IllegalArgumentException("Refresh token hash는 32 byte여야 합니다.");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Refresh token 만료 시각은 발급 시각 후여야 합니다.");
        }
        if (consumedAt != null && consumedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Refresh token 사용 시각은 발급 시각 이전일 수 없습니다.");
        }
        if (revokedAt != null && revokedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Refresh token 폐기 시각은 발급 시각 이전일 수 없습니다.");
        }
    }

    public static RefreshToken issue(
            UUID id,
            UUID accountId,
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new RefreshToken(
                id,
                accountId,
                tokenHash,
                issuedAt,
                expiresAt,
                null,
                null
        );
    }

    public static RefreshToken restore(
            UUID id,
            UUID accountId,
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant consumedAt,
            Instant revokedAt
    ) {
        return new RefreshToken(
                id,
                accountId,
                tokenHash,
                issuedAt,
                expiresAt,
                consumedAt,
                revokedAt
        );
    }

    public RefreshToken consume(Instant now) {
        if (!isActiveAt(now)) {
            throw TokenSessionException.invalidRefreshToken();
        }
        return new RefreshToken(
                id,
                accountId,
                tokenHash,
                issuedAt,
                expiresAt,
                now,
                revokedAt
        );
    }

    public RefreshToken revoke(Instant now) {
        if (revokedAt != null) {
            return this;
        }
        return new RefreshToken(
                id,
                accountId,
                tokenHash,
                issuedAt,
                expiresAt,
                consumedAt,
                now
        );
    }

    public boolean isActiveAt(Instant now) {
        return now != null
                && consumedAt == null
                && revokedAt == null
                && now.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public UUID accountId() {
        return accountId;
    }

    public byte[] tokenHash() {
        return tokenHash.clone();
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

}
