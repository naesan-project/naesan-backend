package com.naesan.transfer.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.transfer.application.ListedTransferRequest;

public record TransferRequestResponse(
        UUID id,
        UUID passportId,
        String requesterEmail,
        String recipientEmail,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransferRequestResponse from(ListedTransferRequest request) {
        return new TransferRequestResponse(
                request.id(),
                request.passportId(),
                request.requesterEmail(),
                request.recipientEmail(),
                request.status().name(),
                request.expiresAt(),
                request.createdAt(),
                request.updatedAt()
        );
    }
}
