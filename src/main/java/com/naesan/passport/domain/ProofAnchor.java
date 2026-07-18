package com.naesan.passport.domain;

import java.time.Instant;
import java.util.UUID;

public final class ProofAnchor {
    private static final int SHA256_BYTE_LENGTH = 32;

    private final UUID id;
    private final UUID passportId;
    private final int schemaVersion;
    private final byte[] anchorSalt;
    private final byte[] commitment;
    private final ProofAnchorState state;
    private final String externalReference;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ProofAnchor(
            UUID id,
            UUID passportId,
            int schemaVersion,
            byte[] anchorSalt,
            byte[] commitment,
            ProofAnchorState state,
            String externalReference,
            Instant createdAt,
            Instant updatedAt
    ) {
        validate(
                id,
                passportId,
                schemaVersion,
                anchorSalt,
                commitment,
                state,
                createdAt,
                updatedAt
        );
        this.id = id;
        this.passportId = passportId;
        this.schemaVersion = schemaVersion;
        this.anchorSalt = anchorSalt.clone();
        this.commitment = commitment.clone();
        this.state = state;
        this.externalReference = externalReference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private static void validate(
            UUID id,
            UUID passportId,
            int schemaVersion,
            byte[] anchorSalt,
            byte[] commitment,
            ProofAnchorState state,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null
                || passportId == null
                || anchorSalt == null
                || commitment == null
                || state == null
                || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Proof anchor 필수 값은 null일 수 없습니다.");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Proof anchor schema version은 0보다 커야 합니다.");
        }
        if (anchorSalt.length != SHA256_BYTE_LENGTH) {
            throw new IllegalArgumentException("Anchor salt는 32 byte여야 합니다.");
        }
        if (commitment.length != SHA256_BYTE_LENGTH) {
            throw new IllegalArgumentException("Commitment는 32 byte여야 합니다.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Proof anchor 수정 시각은 생성 시각보다 빠를 수 없습니다.");
        }
    }

    public static ProofAnchor prepare(
            UUID id,
            UUID passportId,
            AnchorCommitment anchorCommitment,
            Instant preparedAt
    ) {
        return new ProofAnchor(
                id,
                passportId,
                anchorCommitment.schemaVersion(),
                anchorCommitment.anchorSalt(),
                anchorCommitment.commitment(),
                ProofAnchorState.PREPARED,
                null,
                preparedAt,
                preparedAt
        );
    }

    public static ProofAnchor restore(
            UUID id,
            UUID passportId,
            int schemaVersion,
            byte[] anchorSalt,
            byte[] commitment,
            ProofAnchorState state,
            String externalReference,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ProofAnchor(
                id,
                passportId,
                schemaVersion,
                anchorSalt,
                commitment,
                state,
                externalReference,
                createdAt,
                updatedAt
        );
    }

    public ProofAnchor submit(String externalReference, Instant submittedAt) {
        if (state != ProofAnchorState.PREPARED) {
            throw new IllegalStateException("준비된 외부 증명만 제출할 수 있습니다.");
        }
        if (externalReference == null || externalReference.isBlank()) {
            throw new IllegalArgumentException("외부 증명 참조는 비어 있을 수 없습니다.");
        }
        return new ProofAnchor(
                id,
                passportId,
                schemaVersion,
                anchorSalt,
                commitment,
                ProofAnchorState.SUBMITTED,
                externalReference,
                createdAt,
                submittedAt
        );
    }

    public ProofAnchor confirm(Instant confirmedAt) {
        if (state != ProofAnchorState.SUBMITTED) {
            throw new IllegalStateException("제출된 외부 증명만 확정할 수 있습니다.");
        }
        return new ProofAnchor(
                id,
                passportId,
                schemaVersion,
                anchorSalt,
                commitment,
                ProofAnchorState.CONFIRMED,
                externalReference,
                createdAt,
                confirmedAt
        );
    }

    public ProofAnchor awaitReconciliation(Instant changedAt) {
        if (state != ProofAnchorState.PREPARED) {
            throw new IllegalStateException("준비된 외부 증명만 대사 대기로 전환할 수 있습니다.");
        }
        return new ProofAnchor(
                id,
                passportId,
                schemaVersion,
                anchorSalt,
                commitment,
                ProofAnchorState.RECONCILE_PENDING,
                externalReference,
                createdAt,
                changedAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID passportId() {
        return passportId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public byte[] anchorSalt() {
        return anchorSalt.clone();
    }

    public byte[] commitment() {
        return commitment.clone();
    }

    public ProofAnchorState state() {
        return state;
    }

    public String externalReference() {
        return externalReference;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
