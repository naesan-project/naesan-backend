package com.naesan.transfer.application;

import java.time.Instant;
import java.util.UUID;

import com.naesan.transfer.domain.TransferRequestStatus;

public record ListedTransferRequest(
        UUID id,
        UUID passportId,
        String requesterEmail,
        String recipientEmail,
        String productName,
        String merchantName,
        TransferRequestStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
