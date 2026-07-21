package com.naesan.share.domain;

import java.time.Instant;
import java.util.UUID;

public final class PublicShare {
    private static final int TOKEN_HASH_BYTE_LENGTH = 32;

    private final UUID id;
    private final UUID passportId;
    private final byte[] tokenHash;
    private final PublicShareCapability capability;
    private final Instant expiresAt;
    private final Instant revokedAt;
    private final Instant createdAt;

    private PublicShare(
            UUID id,
            UUID passportId,
            byte[] tokenHash,
            PublicShareCapability capability,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt
    ) {
        validate(
                id,
                passportId,
                tokenHash,
                capability,
                expiresAt,
                revokedAt,
                createdAt
        );
        this.id = id;
        this.passportId = passportId;
        this.tokenHash = tokenHash.clone();
        this.capability = capability;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.createdAt = createdAt;
    }

    private static void validate(
            UUID id,
            UUID passportId,
            byte[] tokenHash,
            PublicShareCapability capability,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt
    ) {
        if (id == null
                || passportId == null
                || tokenHash == null
                || capability == null
                || expiresAt == null
                || createdAt == null) {
            throw new IllegalArgumentException("Public share 필수 값은 null일 수 없습니다.");
        }
        if (tokenHash.length != TOKEN_HASH_BYTE_LENGTH) {
            throw new IllegalArgumentException("Public share token hash는 32 byte여야 합니다.");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Public share 만료 시각은 생성 시각보다 늦어야 합니다.");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Public share 폐기 시각은 생성 시각보다 빠를 수 없습니다.");
        }
    }

    public static PublicShare issue(
            UUID id,
            UUID passportId,
            byte[] tokenHash,
            PublicShareCapability capability,
            Instant expiresAt,
            Instant createdAt
    ) {
        return new PublicShare(
                id,
                passportId,
                tokenHash,
                capability,
                expiresAt,
                null,
                createdAt
        );
    }

    public static PublicShare restore(
            UUID id,
            UUID passportId,
            byte[] tokenHash,
            PublicShareCapability capability,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt
    ) {
        return new PublicShare(
                id,
                passportId,
                tokenHash,
                capability,
                expiresAt,
                revokedAt,
                createdAt
        );
    }

    public PublicShare revoke(Instant revokedAt) {
        if (this.revokedAt != null) {
            return this;
        }
        return new PublicShare(
                id,
                passportId,
                tokenHash,
                capability,
                expiresAt,
                revokedAt,
                createdAt
        );
    }

    public boolean isAvailableAt(Instant checkedAt) {
        return revokedAt == null && checkedAt.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public UUID passportId() {
        return passportId;
    }

    public byte[] tokenHash() {
        return tokenHash.clone();
    }

    public PublicShareCapability capability() {
        return capability;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PublicShare publicShare)) {
            return false;
        }
        return id.equals(publicShare.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
