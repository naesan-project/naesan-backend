package com.naesan.passport.domain;

import java.time.Instant;
import java.util.UUID;

public final class OwnershipHistory {
    private final UUID id;
    private final UUID passportId;
    private final UUID previousHolderAccountId;
    private final UUID newHolderAccountId;
    private final OwnershipChangeReason reason;
    private final Instant changedAt;

    private OwnershipHistory(
            UUID id,
            UUID passportId,
            UUID previousHolderAccountId,
            UUID newHolderAccountId,
            OwnershipChangeReason reason,
            Instant changedAt
    ) {
        validate(
                id,
                passportId,
                previousHolderAccountId,
                newHolderAccountId,
                reason,
                changedAt
        );
        this.id = id;
        this.passportId = passportId;
        this.previousHolderAccountId = previousHolderAccountId;
        this.newHolderAccountId = newHolderAccountId;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    private static void validate(
            UUID id,
            UUID passportId,
            UUID previousHolderAccountId,
            UUID newHolderAccountId,
            OwnershipChangeReason reason,
            Instant changedAt
    ) {
        if (id == null
                || passportId == null
                || newHolderAccountId == null
                || reason == null
                || changedAt == null) {
            throw new IllegalArgumentException("소유 이력 필수 값은 null일 수 없습니다.");
        }
        if (reason == OwnershipChangeReason.ISSUED
                && previousHolderAccountId != null) {
            throw new IllegalArgumentException("최초 발급 이력에는 이전 보유자가 없어야 합니다.");
        }
        if (reason == OwnershipChangeReason.TRANSFERRED
                && (previousHolderAccountId == null
                || previousHolderAccountId.equals(newHolderAccountId))) {
            throw new IllegalArgumentException("이전 이력에는 서로 다른 이전·새 보유자가 필요합니다.");
        }
    }

    public static OwnershipHistory recordIssuance(
            UUID id,
            UUID passportId,
            UUID initialHolderAccountId,
            Instant issuedAt
    ) {
        return new OwnershipHistory(
                id,
                passportId,
                null,
                initialHolderAccountId,
                OwnershipChangeReason.ISSUED,
                issuedAt
        );
    }

    public static OwnershipHistory restore(
            UUID id,
            UUID passportId,
            UUID previousHolderAccountId,
            UUID newHolderAccountId,
            OwnershipChangeReason reason,
            Instant changedAt
    ) {
        return new OwnershipHistory(
                id,
                passportId,
                previousHolderAccountId,
                newHolderAccountId,
                reason,
                changedAt
        );
    }

    public static OwnershipHistory recordTransfer(
            UUID id,
            UUID passportId,
            UUID previousHolderAccountId,
            UUID newHolderAccountId,
            Instant changedAt
    ) {
        return new OwnershipHistory(
                id,
                passportId,
                previousHolderAccountId,
                newHolderAccountId,
                OwnershipChangeReason.TRANSFERRED,
                changedAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID passportId() {
        return passportId;
    }

    public UUID previousHolderAccountId() {
        return previousHolderAccountId;
    }

    public UUID newHolderAccountId() {
        return newHolderAccountId;
    }

    public OwnershipChangeReason reason() {
        return reason;
    }

    public Instant changedAt() {
        return changedAt;
    }
}
