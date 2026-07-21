package com.naesan.passport.domain;

import java.time.Instant;
import java.util.UUID;

public final class OutboxReprocessAudit {
    private static final int MAXIMUM_OPERATOR_ID_LENGTH = 100;
    private static final int MAXIMUM_REASON_LENGTH = 500;

    private final UUID id;
    private final UUID outboxEventId;
    private final UUID proofAnchorId;
    private final String operatorId;
    private final String reason;
    private final OutboxEventStatus previousStatus;
    private final OutboxEventStatus newStatus;
    private final int previousAttemptCount;
    private final int reprocessNumber;
    private final Instant requestedAt;

    private OutboxReprocessAudit(
            UUID id,
            UUID outboxEventId,
            UUID proofAnchorId,
            String operatorId,
            String reason,
            OutboxEventStatus previousStatus,
            OutboxEventStatus newStatus,
            int previousAttemptCount,
            int reprocessNumber,
            Instant requestedAt
    ) {
        validate(
                id,
                outboxEventId,
                proofAnchorId,
                operatorId,
                reason,
                previousStatus,
                newStatus,
                previousAttemptCount,
                reprocessNumber,
                requestedAt
        );
        this.id = id;
        this.outboxEventId = outboxEventId;
        this.proofAnchorId = proofAnchorId;
        this.operatorId = operatorId;
        this.reason = reason;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.previousAttemptCount = previousAttemptCount;
        this.reprocessNumber = reprocessNumber;
        this.requestedAt = requestedAt;
    }

    private static void validate(
            UUID id,
            UUID outboxEventId,
            UUID proofAnchorId,
            String operatorId,
            String reason,
            OutboxEventStatus previousStatus,
            OutboxEventStatus newStatus,
            int previousAttemptCount,
            int reprocessNumber,
            Instant requestedAt
    ) {
        if (id == null
                || outboxEventId == null
                || proofAnchorId == null
                || previousStatus == null
                || newStatus == null
                || requestedAt == null) {
            throw new IllegalArgumentException("Outbox 재처리 감사 필수 값은 null일 수 없습니다.");
        }
        if (operatorId == null || operatorId.isBlank()
                || operatorId.length() > MAXIMUM_OPERATOR_ID_LENGTH) {
            throw new IllegalArgumentException("Outbox 재처리 운영자 ID가 올바르지 않습니다.");
        }
        if (reason == null || reason.isBlank()
                || reason.length() > MAXIMUM_REASON_LENGTH) {
            throw new IllegalArgumentException("Outbox 재처리 사유가 올바르지 않습니다.");
        }
        if (previousStatus != OutboxEventStatus.DEAD_LETTER
                && previousStatus != OutboxEventStatus.MANUAL_REVIEW) {
            throw new IllegalArgumentException("감사 대상 이전 상태가 재처리를 허용하지 않습니다.");
        }
        if (newStatus != OutboxEventStatus.PENDING
                && newStatus != OutboxEventStatus.RECONCILE_PENDING) {
            throw new IllegalArgumentException("감사 대상 새 상태가 재처리 상태가 아닙니다.");
        }
        if (previousAttemptCount < 0 || reprocessNumber <= 0) {
            throw new IllegalArgumentException("재처리 시도 횟수와 차수가 올바르지 않습니다.");
        }
    }

    public static OutboxReprocessAudit create(
            UUID id,
            OutboxEvent previousEvent,
            OutboxEvent reprocessedEvent,
            String operatorId,
            String reason,
            int reprocessNumber,
            Instant requestedAt
    ) {
        return new OutboxReprocessAudit(
                id,
                previousEvent.id(),
                previousEvent.proofAnchorId(),
                operatorId,
                reason,
                previousEvent.status(),
                reprocessedEvent.status(),
                previousEvent.attemptCount(),
                reprocessNumber,
                requestedAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID outboxEventId() {
        return outboxEventId;
    }

    public UUID proofAnchorId() {
        return proofAnchorId;
    }

    public String operatorId() {
        return operatorId;
    }

    public String reason() {
        return reason;
    }

    public OutboxEventStatus previousStatus() {
        return previousStatus;
    }

    public OutboxEventStatus newStatus() {
        return newStatus;
    }

    public int previousAttemptCount() {
        return previousAttemptCount;
    }

    public int reprocessNumber() {
        return reprocessNumber;
    }

    public Instant requestedAt() {
        return requestedAt;
    }
}
