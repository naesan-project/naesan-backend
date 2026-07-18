package com.naesan.evidence.domain;

import java.time.Instant;
import java.util.UUID;

public final class EvidenceSnapshot {
    private static final int SHA256_LENGTH = 64;

    private final UUID id;
    private final UUID evidenceId;
    private final int schemaVersion;
    private final byte[] canonicalPayload;
    private final String snapshotDigest;
    private final Instant createdAt;

    public EvidenceSnapshot(
            UUID id,
            UUID evidenceId,
            int schemaVersion,
            byte[] canonicalPayload,
            String snapshotDigest,
            Instant createdAt
    ) {
        validate(
                id,
                evidenceId,
                schemaVersion,
                canonicalPayload,
                snapshotDigest,
                createdAt
        );
        this.id = id;
        this.evidenceId = evidenceId;
        this.schemaVersion = schemaVersion;
        this.canonicalPayload = canonicalPayload.clone();
        this.snapshotDigest = snapshotDigest;
        this.createdAt = createdAt;
    }

    private static void validate(
            UUID id,
            UUID evidenceId,
            int schemaVersion,
            byte[] canonicalPayload,
            String snapshotDigest,
            Instant createdAt
    ) {
        if (id == null || evidenceId == null || createdAt == null) {
            throw new IllegalArgumentException("Evidence snapshot 필수 값은 null일 수 없습니다.");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Snapshot schema version은 0보다 커야 합니다.");
        }
        if (canonicalPayload == null || canonicalPayload.length == 0) {
            throw new IllegalArgumentException("Canonical payload는 비어 있을 수 없습니다.");
        }
        if (snapshotDigest == null
                || snapshotDigest.length() != SHA256_LENGTH
                || !snapshotDigest.chars().allMatch(EvidenceSnapshot::isLowercaseHexadecimal)) {
            throw new IllegalArgumentException("Snapshot digest는 64자 소문자 16진수여야 합니다.");
        }
    }

    private static boolean isLowercaseHexadecimal(int character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f';
    }

    public UUID id() {
        return id;
    }

    public UUID evidenceId() {
        return evidenceId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public byte[] canonicalPayload() {
        return canonicalPayload.clone();
    }

    public String snapshotDigest() {
        return snapshotDigest;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EvidenceSnapshot snapshot)) {
            return false;
        }
        return id.equals(snapshot.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
