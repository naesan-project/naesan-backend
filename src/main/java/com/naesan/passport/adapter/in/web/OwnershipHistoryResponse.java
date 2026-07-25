package com.naesan.passport.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.passport.domain.OwnershipHistory;

public record OwnershipHistoryResponse(
        UUID id,
        UUID previousHolderAccountId,
        UUID newHolderAccountId,
        String reason,
        Instant changedAt
) {

    public static OwnershipHistoryResponse from(OwnershipHistory history) {
        return new OwnershipHistoryResponse(
                history.id(),
                history.previousHolderAccountId(),
                history.newHolderAccountId(),
                history.reason().name(),
                history.changedAt()
        );
    }
}
