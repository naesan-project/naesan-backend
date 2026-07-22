package com.naesan.transfer.domain;

import java.time.Instant;
import java.util.UUID;

public final class TransferRequest {
    private final UUID id;
    private final UUID passportId;
    private final UUID requesterAccountId;
    private final UUID recipientAccountId;
    private final TransferRequestStatus status;
    private final long version;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TransferRequest(
            UUID id,
            UUID passportId,
            UUID requesterAccountId,
            UUID recipientAccountId,
            TransferRequestStatus status,
            long version,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        validate(
                id,
                passportId,
                requesterAccountId,
                recipientAccountId,
                status,
                version,
                expiresAt,
                createdAt,
                updatedAt
        );
        this.id = id;
        this.passportId = passportId;
        this.requesterAccountId = requesterAccountId;
        this.recipientAccountId = recipientAccountId;
        this.status = status;
        this.version = version;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private static void validate(
            UUID id,
            UUID passportId,
            UUID requesterAccountId,
            UUID recipientAccountId,
            TransferRequestStatus status,
            long version,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null
                || passportId == null
                || requesterAccountId == null
                || recipientAccountId == null
                || status == null
                || expiresAt == null
                || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Transfer request 필수 값은 null일 수 없습니다.");
        }
        if (requesterAccountId.equals(recipientAccountId)) {
            throw new IllegalArgumentException("자기 자신에게 소유권 이전을 요청할 수 없습니다.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Transfer request version은 0 이상이어야 합니다.");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Transfer request 만료 시각은 생성 시각보다 늦어야 합니다.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Transfer request 수정 시각은 생성 시각보다 빠를 수 없습니다.");
        }
    }

    public static TransferRequest create(
            UUID id,
            UUID passportId,
            UUID requesterAccountId,
            UUID recipientAccountId,
            Instant expiresAt,
            Instant createdAt
    ) {
        return new TransferRequest(
                id,
                passportId,
                requesterAccountId,
                recipientAccountId,
                TransferRequestStatus.PENDING,
                0,
                expiresAt,
                createdAt,
                createdAt
        );
    }

    public static TransferRequest restore(
            UUID id,
            UUID passportId,
            UUID requesterAccountId,
            UUID recipientAccountId,
            TransferRequestStatus status,
            long version,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new TransferRequest(
                id,
                passportId,
                requesterAccountId,
                recipientAccountId,
                status,
                version,
                expiresAt,
                createdAt,
                updatedAt
        );
    }

    public TransferRequest expireIfDue(Instant changedAt) {
        if (status != TransferRequestStatus.PENDING
                || changedAt.isBefore(expiresAt)) {
            return this;
        }
        return transitionTo(TransferRequestStatus.EXPIRED, changedAt);
    }

    private TransferRequest transitionTo(
            TransferRequestStatus changedStatus,
            Instant changedAt
    ) {
        return new TransferRequest(
                id,
                passportId,
                requesterAccountId,
                recipientAccountId,
                changedStatus,
                version + 1,
                expiresAt,
                createdAt,
                changedAt
        );
    }

    public TransferRequest cancelBy(UUID accountId, Instant changedAt) {
        requirePendingAt(changedAt);
        if (!requesterAccountId.equals(accountId)) {
            throw new IllegalStateException("요청자만 소유권 이전을 취소할 수 있습니다.");
        }
        return transitionTo(TransferRequestStatus.CANCELLED, changedAt);
    }

    private void requirePendingAt(Instant changedAt) {
        if (status != TransferRequestStatus.PENDING
                || !changedAt.isBefore(expiresAt)) {
            throw new IllegalStateException("대기 중인 소유권 이전 요청만 처리할 수 있습니다.");
        }
    }

    public TransferRequest rejectBy(UUID accountId, Instant changedAt) {
        requirePendingAt(changedAt);
        if (!recipientAccountId.equals(accountId)) {
            throw new IllegalStateException("수신자만 소유권 이전을 거절할 수 있습니다.");
        }
        return transitionTo(TransferRequestStatus.REJECTED, changedAt);
    }

    public boolean isPendingAt(Instant checkedAt) {
        return status == TransferRequestStatus.PENDING
                && checkedAt.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public UUID passportId() {
        return passportId;
    }

    public UUID requesterAccountId() {
        return requesterAccountId;
    }

    public UUID recipientAccountId() {
        return recipientAccountId;
    }

    public TransferRequestStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant expiresAt() {
        return expiresAt;
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
        if (!(object instanceof TransferRequest transferRequest)) {
            return false;
        }
        return id.equals(transferRequest.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
