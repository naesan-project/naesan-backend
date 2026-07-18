package com.naesan.evidence.domain;

import java.time.Instant;
import java.util.UUID;

public final class EvidenceFile {
    private static final int SHA256_LENGTH = 64;

    private final UUID id;
    private final UUID evidenceId;
    private final StorageKey objectKey;
    private final String sha256;
    private final EvidenceFileType fileType;
    private final long size;
    private final EvidenceFileState state;
    private final Instant createdAt;
    private final Instant updatedAt;

    private EvidenceFile(
            UUID id,
            UUID evidenceId,
            StorageKey objectKey,
            String sha256,
            EvidenceFileType fileType,
            long size,
            EvidenceFileState state,
            Instant createdAt,
            Instant updatedAt
    ) {
        validate(id, evidenceId, objectKey, sha256, fileType, size, state, createdAt, updatedAt);
        this.id = id;
        this.evidenceId = evidenceId;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.fileType = fileType;
        this.size = size;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private static void validate(
            UUID id,
            UUID evidenceId,
            StorageKey objectKey,
            String sha256,
            EvidenceFileType fileType,
            long size,
            EvidenceFileState state,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null
                || evidenceId == null
                || objectKey == null
                || fileType == null
                || state == null
                || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Evidence 파일의 필수 값은 null일 수 없습니다.");
        }
        if (sha256 == null
                || sha256.length() != SHA256_LENGTH
                || !sha256.chars().allMatch(EvidenceFile::isLowercaseHexadecimal)) {
            throw new IllegalArgumentException("SHA-256은 64자 소문자 16진수여야 합니다.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Evidence 파일 크기는 0보다 커야 합니다.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("파일 수정 시각은 생성 시각보다 빠를 수 없습니다.");
        }
    }

    private static boolean isLowercaseHexadecimal(int character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f';
    }

    public static EvidenceFile createTemporary(
            UUID id,
            UUID evidenceId,
            StorageKey objectKey,
            String sha256,
            EvidenceFileType fileType,
            long size,
            Instant createdAt
    ) {
        return new EvidenceFile(
                id,
                evidenceId,
                objectKey,
                sha256,
                fileType,
                size,
                EvidenceFileState.TEMPORARY,
                createdAt,
                createdAt
        );
    }

    public static EvidenceFile restore(
            UUID id,
            UUID evidenceId,
            StorageKey objectKey,
            String sha256,
            EvidenceFileType fileType,
            long size,
            EvidenceFileState state,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new EvidenceFile(
                id,
                evidenceId,
                objectKey,
                sha256,
                fileType,
                size,
                state,
                createdAt,
                updatedAt
        );
    }

    public EvidenceFile promote(StorageKey permanentKey, Instant promotedAt) {
        if (state != EvidenceFileState.TEMPORARY) {
            throw new IllegalStateException("임시 파일만 승격할 수 있습니다.");
        }
        return new EvidenceFile(
                id,
                evidenceId,
                permanentKey,
                sha256,
                fileType,
                size,
                EvidenceFileState.PROMOTED,
                createdAt,
                promotedAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID evidenceId() {
        return evidenceId;
    }

    public StorageKey objectKey() {
        return objectKey;
    }

    public String sha256() {
        return sha256;
    }

    public EvidenceFileType fileType() {
        return fileType;
    }

    public long size() {
        return size;
    }

    public EvidenceFileState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EvidenceFile evidenceFile)) {
            return false;
        }
        return id.equals(evidenceFile.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
