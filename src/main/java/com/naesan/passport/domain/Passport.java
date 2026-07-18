package com.naesan.passport.domain;

import java.time.Instant;
import java.util.UUID;

public final class Passport {
    private final UUID id;
    private final UUID snapshotId;
    private final UUID currentHolderAccountId;
    private final PassportStatus status;
    private final long version;
    private final Instant createdAt;

    private Passport(
            UUID id,
            UUID snapshotId,
            UUID currentHolderAccountId,
            PassportStatus status,
            long version,
            Instant createdAt
    ) {
        validate(id, snapshotId, currentHolderAccountId, status, version, createdAt);
        this.id = id;
        this.snapshotId = snapshotId;
        this.currentHolderAccountId = currentHolderAccountId;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
    }

    private static void validate(
            UUID id,
            UUID snapshotId,
            UUID currentHolderAccountId,
            PassportStatus status,
            long version,
            Instant createdAt
    ) {
        if (id == null
                || snapshotId == null
                || currentHolderAccountId == null
                || status == null
                || createdAt == null) {
            throw new IllegalArgumentException("Passport 필수 값은 null일 수 없습니다.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Passport version은 0 이상이어야 합니다.");
        }
    }

    public static Passport issue(
            UUID id,
            UUID snapshotId,
            UUID initialHolderAccountId,
            Instant issuedAt
    ) {
        return new Passport(
                id,
                snapshotId,
                initialHolderAccountId,
                PassportStatus.ACTIVE,
                0,
                issuedAt
        );
    }

    public static Passport restore(
            UUID id,
            UUID snapshotId,
            UUID currentHolderAccountId,
            PassportStatus status,
            long version,
            Instant createdAt
    ) {
        return new Passport(
                id,
                snapshotId,
                currentHolderAccountId,
                status,
                version,
                createdAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public UUID currentHolderAccountId() {
        return currentHolderAccountId;
    }

    public PassportStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Passport passport)) {
            return false;
        }
        return id.equals(passport.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
