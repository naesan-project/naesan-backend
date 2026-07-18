package com.naesan.evidence.domain;

import java.time.Instant;
import java.util.UUID;

public final class PurchaseEvidence {
    private final UUID id;
    private final UUID ownerAccountId;
    private final EvidenceMetadata metadata;
    private final PurchaseEvidenceState state;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant confirmedAt;

    private PurchaseEvidence(
            UUID id,
            UUID ownerAccountId,
            EvidenceMetadata metadata,
            PurchaseEvidenceState state,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt
    ) {
        validate(
                id,
                ownerAccountId,
                metadata,
                state,
                version,
                createdAt,
                updatedAt,
                confirmedAt
        );
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.metadata = metadata;
        this.state = state;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.confirmedAt = confirmedAt;
    }

    private static void validate(
            UUID id,
            UUID ownerAccountId,
            EvidenceMetadata metadata,
            PurchaseEvidenceState state,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt
    ) {
        if (id == null
                || ownerAccountId == null
                || metadata == null
                || state == null
                || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("구매 증빙의 필수 값은 null일 수 없습니다.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("구매 증빙 version은 0 이상이어야 합니다.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("수정 시각은 생성 시각보다 빠를 수 없습니다.");
        }
        if ((state == PurchaseEvidenceState.CONFIRMED) != (confirmedAt != null)) {
            throw new IllegalArgumentException("확정 상태와 확정 시각이 일치해야 합니다.");
        }
    }

    public static PurchaseEvidence createDraft(
            UUID id,
            UUID ownerAccountId,
            EvidenceMetadata metadata,
            Instant createdAt
    ) {
        return new PurchaseEvidence(
                id,
                ownerAccountId,
                metadata,
                PurchaseEvidenceState.DRAFT,
                0,
                createdAt,
                createdAt,
                null
        );
    }

    public static PurchaseEvidence restore(
            UUID id,
            UUID ownerAccountId,
            EvidenceMetadata metadata,
            PurchaseEvidenceState state,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt
    ) {
        return new PurchaseEvidence(
                id,
                ownerAccountId,
                metadata,
                state,
                version,
                createdAt,
                updatedAt,
                confirmedAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID ownerAccountId() {
        return ownerAccountId;
    }

    public EvidenceMetadata metadata() {
        return metadata;
    }

    public PurchaseEvidenceState state() {
        return state;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PurchaseEvidence evidence)) {
            return false;
        }
        return id.equals(evidence.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
