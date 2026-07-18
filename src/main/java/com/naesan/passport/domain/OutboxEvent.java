package com.naesan.passport.domain;

import java.time.Instant;
import java.util.UUID;

public final class OutboxEvent {
    public static final String PROOF_ANCHOR_REQUESTED = "PROOF_ANCHOR_REQUESTED";

    private final UUID id;
    private final String eventType;
    private final UUID aggregateId;
    private final UUID proofAnchorId;
    private final int schemaVersion;
    private final String payload;
    private final String dispatchKey;
    private final OutboxEventStatus status;
    private final int attemptCount;
    private final Instant nextAttemptAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OutboxEvent(
            UUID id,
            String eventType,
            UUID aggregateId,
            UUID proofAnchorId,
            int schemaVersion,
            String payload,
            String dispatchKey,
            OutboxEventStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        validate(
                id,
                eventType,
                aggregateId,
                proofAnchorId,
                schemaVersion,
                payload,
                dispatchKey,
                status,
                attemptCount,
                nextAttemptAt,
                createdAt,
                updatedAt
        );
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.proofAnchorId = proofAnchorId;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.dispatchKey = dispatchKey;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private static void validate(
            UUID id,
            String eventType,
            UUID aggregateId,
            UUID proofAnchorId,
            int schemaVersion,
            String payload,
            String dispatchKey,
            OutboxEventStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null
                || aggregateId == null
                || proofAnchorId == null
                || status == null
                || nextAttemptAt == null
                || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Outbox event 필수 값은 null일 수 없습니다.");
        }
        if (eventType == null || eventType.isBlank()
                || payload == null || payload.isBlank()
                || dispatchKey == null || dispatchKey.isBlank()) {
            throw new IllegalArgumentException("Outbox event 문자열 값은 비어 있을 수 없습니다.");
        }
        if (schemaVersion <= 0 || attemptCount < 0) {
            throw new IllegalArgumentException("Outbox event version과 시도 횟수가 올바르지 않습니다.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Outbox event 수정 시각은 생성 시각보다 빠를 수 없습니다.");
        }
    }

    public static OutboxEvent createProofAnchorRequest(
            UUID id,
            UUID passportId,
            UUID proofAnchorId,
            int schemaVersion,
            String payload,
            String dispatchKey,
            Instant createdAt
    ) {
        return new OutboxEvent(
                id,
                PROOF_ANCHOR_REQUESTED,
                passportId,
                proofAnchorId,
                schemaVersion,
                payload,
                dispatchKey,
                OutboxEventStatus.PENDING,
                0,
                createdAt,
                createdAt,
                createdAt
        );
    }

    public static OutboxEvent restore(
            UUID id,
            String eventType,
            UUID aggregateId,
            UUID proofAnchorId,
            int schemaVersion,
            String payload,
            String dispatchKey,
            OutboxEventStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new OutboxEvent(
                id,
                eventType,
                aggregateId,
                proofAnchorId,
                schemaVersion,
                payload,
                dispatchKey,
                status,
                attemptCount,
                nextAttemptAt,
                createdAt,
                updatedAt
        );
    }

    public OutboxEvent succeed(Instant succeededAt) {
        if (status != OutboxEventStatus.CLAIMED) {
            throw new IllegalStateException("Claim된 Outbox event만 완료할 수 있습니다.");
        }
        return new OutboxEvent(
                id,
                eventType,
                aggregateId,
                proofAnchorId,
                schemaVersion,
                payload,
                dispatchKey,
                OutboxEventStatus.SUCCEEDED,
                attemptCount,
                nextAttemptAt,
                createdAt,
                succeededAt
        );
    }

    public UUID id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public UUID aggregateId() {
        return aggregateId;
    }

    public UUID proofAnchorId() {
        return proofAnchorId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String payload() {
        return payload;
    }

    public String dispatchKey() {
        return dispatchKey;
    }

    public OutboxEventStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
